package com.schedule.app.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schedule.app.ai.dto.AiChatRequest;
import com.schedule.app.ai.dto.AiFillRequest;
import com.schedule.app.ai.entity.AiConversation;
import com.schedule.app.ai.repository.AiConversationRepository;
import com.schedule.app.entity.*;
import com.schedule.app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private final AiConversationRepository conversationRepository;
    private final ScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String openAiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    @Transactional
    public void chat(AiChatRequest request, User user, SseEmitter emitter) {
        try {
            // ── FIX #2: История привязана к scheduleId, а не только к userId ──
            Long scheduleId = request.getScheduleId();
            List<AiConversation> history = scheduleId != null
                    ? conversationRepository.findTop20ByUserIdAndScheduleIdOrderByCreatedAtAsc(user.getId(), scheduleId)
                    : conversationRepository.findTop20ByUserIdAndScheduleIdIsNullOrderByCreatedAtAsc(user.getId());

            // 1. Собираем контекст из БД
            String systemContext = buildSystemContext(user, request);

            // 2. Строим массив messages для OpenAI
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemContext));

            for (AiConversation h : history) {
                messages.add(Map.of("role", h.getRole(), "content", h.getContent()));
            }
            messages.add(Map.of("role", "user", "content", request.getMessage()));

            // 3. Сохраняем сообщение пользователя
            conversationRepository.save(AiConversation.builder()
                    .user(user)
                    .scheduleId(scheduleId) // ← FIX #2
                    .role("user")
                    .content(request.getMessage())
                    .build());

            // 4. Определяем интент
            String intentJson = detectIntent(messages);
            JsonNode intentNode = objectMapper.readTree(intentJson);
            String intent = intentNode.has("intent") ? intentNode.get("intent").asText() : "chat";
            String replyText = intentNode.has("text") ? intentNode.get("text").asText() : "";

            // 5. Стримим текстовый ответ — FIX #3: отправляем только новое слово (дельта)
            if (!replyText.isEmpty()) {
                streamText(replyText, emitter);
            }

            // 6. Сохраняем ответ ассистента
            conversationRepository.save(AiConversation.builder()
                    .user(user)
                    .scheduleId(scheduleId) // ← FIX #2
                    .role("assistant")
                    .content(replyText)
                    .build());

            // 7. Выполняем действие если нужно
            if ("fill_schedule".equals(intent)) {
                // ── FIX #5: scheduleId только из request, не доверяем AI ──
                if (scheduleId == null) {
                    emitter.send(SseEmitter.event().name("text")
                            .data(" Открой нужный график и напиши снова — я вижу на какой ты странице."));
                    emitter.complete();
                    return;
                }

                AiFillRequest fillRequest = new AiFillRequest();
                fillRequest.setScheduleId(scheduleId);
                fillRequest.setInstructions(buildInstructionsFromHistory(history, request.getMessage()));
                aiService.fillScheduleStreaming(fillRequest, emitter);
                return; // fillScheduleStreaming сам вызывает emitter.complete()
            }

            emitter.complete();

        } catch (Exception e) {
            log.error("AI chat error", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    // ── FIX #4: clearHistory с опциональным scheduleId ──
    @Transactional
    public void clearHistory(User user, Long scheduleId) {
        if (scheduleId != null) {
            conversationRepository.deleteAllByUserIdAndScheduleId(user.getId(), scheduleId);
        } else {
            conversationRepository.deleteAllByUserId(user.getId());
        }
    }

    // ── Системный контекст ──
    private String buildSystemContext(User user, AiChatRequest request) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("Ты — умный AI-ассистент HR-системы WorGraph. ");
        ctx.append("Отвечай на русском языке, кратко и по делу.\n\n");

        ctx.append("ТЕКУЩИЙ ПОЛЬЗОВАТЕЛЬ:\n");
        ctx.append("- Имя: ").append(user.getUsername()).append("\n");
        ctx.append("- Роль: MANAGER\n\n");

        try {
            List<Branch> branches = branchRepository.findAll();
            ctx.append("ФИЛИАЛЫ В СИСТЕМЕ (").append(branches.size()).append("):\n");
            for (Branch b : branches) {
                long empCount = employeeRepository.findAllByBranchIdAndIsActiveTrue(b.getId()).size();
                ctx.append("- #").append(b.getId()).append(" ").append(b.getName())
                   .append(" (").append(empCount).append(" сотр.)\n");
            }
            ctx.append("\n");
        } catch (Exception e) {
            log.warn("Could not load branches for AI context", e);
        }

        try {
            List<Schedule> schedules = scheduleRepository.findAll();
            List<Schedule> recent = schedules.stream()
                    .sorted(Comparator.comparing(Schedule::getId).reversed())
                    .limit(5)
                    .toList();
            ctx.append("ПОСЛЕДНИЕ ГРАФИКИ:\n");
            for (Schedule s : recent) {
                ctx.append("- #").append(s.getId())
                   .append(" ").append(s.getBranch().getName())
                   .append(" ").append(s.getMonth()).append("/").append(s.getYear())
                   .append(" [").append(s.getStatus()).append("]\n");
            }
            ctx.append("\n");
        } catch (Exception e) {
            log.warn("Could not load schedules for AI context", e);
        }

        if (request.getScheduleId() != null) {
            try {
                scheduleRepository.findById(request.getScheduleId()).ifPresent(s -> {
                    ctx.append("ОТКРЫТЫЙ ГРАФИК ПРЯМО СЕЙЧАС:\n");
                    ctx.append("- ID: #").append(s.getId()).append("\n");
                    ctx.append("- Филиал: ").append(s.getBranch().getName()).append("\n");
                    ctx.append("- Период: ").append(s.getMonth()).append("/").append(s.getYear()).append("\n");
                    ctx.append("- Статус: ").append(s.getStatus()).append("\n");

                    List<Employee> emps = employeeRepository
                            .findAllByBranchIdAndIsActiveTrue(s.getBranch().getId());
                    ctx.append("- Сотрудники (").append(emps.size()).append("):\n");
                    for (Employee e : emps) {
                        ctx.append("  * #").append(e.getId())
                           .append(" ").append(e.getLastName()).append(" ").append(e.getFirstName())
                           .append(" — ").append(e.getPosition()).append("\n");
                    }
                    ctx.append("\n");
                });
            } catch (Exception e) {
                log.warn("Could not load schedule context", e);
            }
        }

        ctx.append("ТВОИ ВОЗМОЖНОСТИ:\n");
        ctx.append("- Заполнить/расставить смены в открытом графике (интент: fill_schedule)\n");
        ctx.append("- Ответить на вопросы о системе и графиках\n\n");

        ctx.append("ВАЖНО:\n");
        ctx.append("- Когда пользователь говорит про смены, расписание, декрет, отпуск — это fill_schedule\n");
        // ── FIX #5: явно запрещаем AI самостоятельно выбирать scheduleId ──
        ctx.append("- scheduleId в ответе ВСЕГДА возвращай null — система сама знает какой график открыт\n");
        ctx.append("Отвечай ТОЛЬКО в JSON: {\"intent\":\"fill_schedule|chat\", \"text\":\"твой ответ\", \"scheduleId\":null}\n");

        return ctx.toString();
    }

    // ── FIX #6: HTTP таймауты ──
    private String detectIntent(List<Map<String, String>> messages) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", "gpt-4o-mini",
                "messages", messages,
                "temperature", 0.3,
                "response_format", Map.of("type", "json_object")
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI error: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.get("choices").get(0).get("message").get("content").asText();
    }

    // ── FIX #3: стримим только дельту (одно слово за раз), не накопленный текст ──
    private void streamText(String text, SseEmitter emitter) throws Exception {
        String[] words = text.split(" ");
        for (String word : words) {
            emitter.send(SseEmitter.event().name("text").data(word + " "));
            Thread.sleep(30);
        }
    }

    private String buildInstructionsFromHistory(List<AiConversation> history, String currentMessage) {
        List<String> userMessages = history.stream()
                .filter(h -> "user".equals(h.getRole()))
                .map(AiConversation::getContent)
                .collect(Collectors.toList());
        userMessages.add(currentMessage);

        int from = Math.max(0, userMessages.size() - 3);
        return String.join(". ", userMessages.subList(from, userMessages.size()));
    }
}