package com.schedule.app.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schedule.app.ai.dto.AiFillRequest;
import com.schedule.app.entity.Employee;
import com.schedule.app.entity.Schedule;
import com.schedule.app.entity.ScheduleEntry;
import com.schedule.app.enums.ScheduleStatus;
import com.schedule.app.repository.EmployeeRepository;
import com.schedule.app.repository.ScheduleEntryRepository;
import com.schedule.app.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String openAiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final Set<String> SPECIAL_TYPES = Set.of("О", "Б", "Д", "БС", "К");

    @Transactional
    public void fillScheduleStreaming(AiFillRequest request, SseEmitter emitter) {
        try {
            // ── 1. Загружаем данные из БД ──
            Schedule schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> new RuntimeException("График не найден"));

            // ── FIX #1: Защита утверждённого/архивного графика ──
            if (schedule.getStatus() == ScheduleStatus.APPROVED ||
                schedule.getStatus() == ScheduleStatus.ARCHIVE) {
                emitter.send(SseEmitter.event().name("error")
                        .data("Нельзя изменять утверждённый или архивный график"));
                emitter.complete();
                return;
            }

            List<Employee> employees = employeeRepository
                    .findAllByBranchIdAndIsActiveTrue(schedule.getBranch().getId());

            if (employees.isEmpty()) {
                emitter.send(SseEmitter.event().name("error")
                        .data("Нет активных сотрудников в этом филиале"));
                emitter.complete();
                return;
            }

            sendStatus(emitter, "Загружаю данные для " + employees.size() + " сотрудников...");

            // ── 2. Строим календарь месяца ──
            int year = schedule.getYear();
            int month = schedule.getMonth();
            int daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth();

            List<String> weekendDates = new ArrayList<>();
            List<String> workDates = new ArrayList<>();

            for (int d = 1; d <= daysInMonth; d++) {
                LocalDate date = LocalDate.of(year, month, d);
                String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
                DayOfWeek dow = date.getDayOfWeek();
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    weekendDates.add(dateStr);
                } else {
                    workDates.add(dateStr);
                }
            }

            // ── 3. Собираем зафиксированные ячейки из БД (О, Б, Д, БС, К) ──
            Map<String, String> fixedCells = new HashMap<>();
            List<ScheduleEntry> existing = scheduleEntryRepository.findAllByScheduleId(schedule.getId());
            for (ScheduleEntry e : existing) {
                if (SPECIAL_TYPES.contains(e.getShiftType())) {
                    String key = e.getEmployee().getId() + "_" + e.getWorkDate();
                    fixedCells.put(key, e.getShiftType());
                }
            }

            sendStatus(emitter, "Отправляю запрос к AI...");

            // ── 4. Строим промпт ──
            String employeeJson = employees.stream()
                    .map(e -> String.format(
                            "{\"id\":%d,\"name\":\"%s %s\",\"position\":\"%s\"}",
                            e.getId(), e.getLastName(), e.getFirstName(), e.getPosition()))
                    .collect(Collectors.joining(",", "[", "]"));

            String fixedJson = fixedCells.isEmpty() ? "{}" :
                    fixedCells.entrySet().stream()
                            .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                            .collect(Collectors.joining(",", "{", "}"));

            int targetWorkDays = (int) (workDates.size() * 0.9);

            String prompt = String.format("""
                    Ты — система составления рабочих графиков. Верни ТОЛЬКО валидный JSON без лишнего текста.
                    
                    ДАННЫЕ:
                    - Период: %d/%d, всего дней: %d
                    - Рабочие дни пн-пт: %s
                    - Выходные сб-вс: %s
                    - Сотрудники: %s
                    - Зафиксировано (НЕ ТРОГАТЬ): %s
                    - Типы смен: "9-18", "8-17", "9-21", "8-20", "В" (выходной), "Д" (декрет), "О" (отпуск), "Б" (больничный)
                    - Особые условия: %s
                    
                    ПРАВИЛА (строго):
                    1. Сб и вс — ВСЕГДА "В" для всех, без исключений
                    2. Зафиксированные ячейки НЕ трогать
                    3. Если в условиях сказано что сотрудник в декрете/отпуске — ставь "Д"/"О" на ВСЕ дни месяца
                    4. Норма рабочих дней ~%d (±2)
                    5. Чередуй типы смен равномерно между сотрудниками
                    6. Генерируй записи для ВСЕХ сотрудников на ВСЕ дни месяца
                    
                    ФОРМАТ (только JSON):
                    {"entries":[{"employeeId":1,"workDate":"2026-03-01","shiftType":"9-18"}]}
                    """,
                    month, year, daysInMonth,
                    String.join(", ", workDates),
                    String.join(", ", weekendDates),
                    employeeJson,
                    fixedJson,
                    request.getInstructions() != null ? request.getInstructions() : "нет",
                    targetWorkDays
            );

            // ── 5. Вызываем OpenAI ──
            String aiResponse = callOpenAI(prompt);
            sendStatus(emitter, "Применяю изменения...");

            // ── 6. Парсим ответ ──
            JsonNode root = objectMapper.readTree(aiResponse);
            JsonNode entries = root.get("entries");
            if (entries == null || !entries.isArray()) {
                throw new RuntimeException("AI вернул некорректный формат");
            }

            // ── 7. Удаляем старые записи (кроме зафиксированных) ──
            List<ScheduleEntry> toDelete = existing.stream()
                    .filter(e -> !SPECIAL_TYPES.contains(e.getShiftType()))
                    .toList();
            scheduleEntryRepository.deleteAll(toDelete);
            scheduleEntryRepository.flush();

            // ── 8. Группируем новые записи, дедупликация через Map ──
            Map<String, ScheduleEntry> uniqueEntries = new LinkedHashMap<>();

            for (JsonNode entryNode : entries) {
                long empId = entryNode.get("employeeId").asLong();
                String dateStr = entryNode.get("workDate").asText();
                String shiftType = entryNode.get("shiftType").asText();

                // Не перезаписываем зафиксированные
                if (fixedCells.containsKey(empId + "_" + dateStr)) continue;

                // Принудительно: выходные всегда "В"
                if (weekendDates.contains(dateStr) && !SPECIAL_TYPES.contains(shiftType)) {
                    shiftType = "В";
                }

                Employee emp = employees.stream()
                        .filter(e -> e.getId().equals(empId))
                        .findFirst().orElse(null);
                if (emp == null) continue;

                uniqueEntries.put(empId + "_" + dateStr, ScheduleEntry.builder()
                        .schedule(schedule)
                        .employee(emp)
                        .workDate(LocalDate.parse(dateStr))
                        .shiftType(shiftType)
                        .build());
            }

            // Группируем по сотруднику
            Map<Long, List<ScheduleEntry>> byEmployee = new LinkedHashMap<>();
            for (ScheduleEntry se : uniqueEntries.values()) {
                byEmployee.computeIfAbsent(se.getEmployee().getId(), k -> new ArrayList<>()).add(se);
            }

            // ── 9. Сохраняем по сотруднику и стримим прогресс ──
            int totalSaved = 0;
            for (Map.Entry<Long, List<ScheduleEntry>> empEntry : byEmployee.entrySet()) {
                scheduleEntryRepository.saveAll(empEntry.getValue());
                totalSaved += empEntry.getValue().size();

                Employee emp = employees.stream()
                        .filter(e -> e.getId().equals(empEntry.getKey()))
                        .findFirst().orElse(null);
                if (emp == null) continue;

                long workDaysCount = empEntry.getValue().stream()
                        .filter(e -> !e.getShiftType().equals("В") && !SPECIAL_TYPES.contains(e.getShiftType()))
                        .count();
                long decretDays = empEntry.getValue().stream()
                        .filter(e -> e.getShiftType().equals("Д"))
                        .count();

                String progressMsg = String.format(
                        "{\"employeeId\":%d,\"name\":\"%s %s\",\"workDays\":%d,\"decret\":%d,\"total\":%d}",
                        emp.getId(), emp.getLastName(), emp.getFirstName(),
                        workDaysCount, decretDays, totalSaved
                );
                emitter.send(SseEmitter.event().name("progress").data(progressMsg));

                Thread.sleep(150);
            }

            // ── 10. Итог ──
            String summary = byEmployee.entrySet().stream()
                    .map(e -> {
                        Employee emp = employees.stream()
                                .filter(em -> em.getId().equals(e.getKey()))
                                .findFirst().orElse(null);
                        if (emp == null) return "";
                        long dec = e.getValue().stream().filter(en -> en.getShiftType().equals("Д")).count();
                        long wd = e.getValue().stream()
                                .filter(en -> !en.getShiftType().equals("В") && !SPECIAL_TYPES.contains(en.getShiftType()))
                                .count();
                        return dec > 0
                                ? emp.getLastName() + ": декрет"
                                : emp.getLastName() + ": " + wd + "р.д.";
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));

            String doneMsg = String.format("{\"filledCells\":%d,\"summary\":\"%s\"}", totalSaved, summary);
            emitter.send(SseEmitter.event().name("done").data(doneMsg));
            emitter.complete();

        } catch (Exception e) {
            log.error("AI fill schedule error", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    private void sendStatus(SseEmitter emitter, String message) throws Exception {
        emitter.send(SseEmitter.event().name("status").data(message));
    }

    private String callOpenAI(String prompt) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3,
                "response_format", Map.of("type", "json_object")
        ));

        // ── FIX #6: HTTP таймауты ──
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiKey)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI error " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.get("choices").get(0).get("message").get("content").asText();
    }
}