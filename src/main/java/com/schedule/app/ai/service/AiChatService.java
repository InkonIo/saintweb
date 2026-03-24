package com.schedule.app.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schedule.app.ai.dto.AiChatRequest;
import com.schedule.app.ai.service.SseTextStreamer;
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
    private static final int MAX_TOOL_ITERATIONS = 5;

    // ─────────────────────────────────────────────────────────
    //  TOOL DEFINITIONS
    // ─────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildToolDefinitions() {
        return List.of(
            tool("fill_schedule",
                "Fills the work schedule with shifts for all branch employees. " +
                "Call when user wants to assign shifts, fill a schedule, or mentions vacation/sick leave.",
                props(
                    param("scheduleId", "integer", "ID of the schedule to fill"),
                    param("instructions", "string", "Special instructions: who is on leave, how to rotate shifts, etc.")
                ),
                List.of("scheduleId")
            ),
            tool("get_schedule_stats",
                "Shows schedule statistics: working days per employee, who is on leave. " +
                "Call when asked about shift counts or who is working.",
                props(
                    param("scheduleId", "integer", "Schedule ID")
                ),
                List.of("scheduleId")
            ),
            tool("list_employees",
                "Returns the list of employees in a branch. " +
                "Call when user asks who works here or wants to check team composition.",
                props(
                    param("branchId", "integer", "Branch ID to filter by")
                ),
                List.of("branchId")
            ),
            tool("list_schedules",
                "Returns the list of schedules. Call when user needs to find a specific schedule.",
                props(
                    param("branchId", "integer", "Filter by branch (optional)"),
                    param("month", "integer", "Filter by month 1-12 (optional)"),
                    param("year", "integer", "Filter by year (optional)")
                ),
                List.of()
            ),
            tool("wizard_create_branch",
                "Shows a UI card for the user to create a new branch. " +
                "Call when user wants to create a branch, setup from scratch, or create everything new.",
                props(
                    param("name", "string", "Suggested branch name"),
                    param("address", "string", "Suggested branch address")
                ),
                List.of("name", "address")
            ),
            tool("wizard_create_template",
                "Shows a UI card for the user to create a schedule template.",
                props(
                    param("name", "string", "Suggested template name"),
                    param("description", "string", "Suggested description")
                ),
                List.of("name", "description")
            ),
            tool("wizard_create_employees",
                "Shows a UI card for the user to add employees to a branch. " +
                "Call after branch is created or when user wants to add employees.",
                props(
                    param("employees", "string", "JSON array of employees: [{firstName,lastName,position}]")
                ),
                List.of("employees")
            ),
            tool("wizard_create_schedule",
                "Shows a UI card for the user to create a new schedule.",
                props(
                    param("month", "integer", "Suggested month 1-12"),
                    param("year", "integer", "Suggested year"),
                    param("branchId", "integer", "Branch ID (use from wizardCtx if available)"),
                    param("templateId", "integer", "Template ID (use from wizardCtx if available)")
                ),
                List.of("month", "year")
            )
        );
    }

    // ─────────────────────────────────────────────────────────
    //  MAIN: ReAct loop
    //  No @Transactional — SSE can stream for minutes,
    //  holding a DB connection that long causes timeouts.
    //  All DB work is done in short @Transactional helpers.
    // ─────────────────────────────────────────────────────────

    public void chat(AiChatRequest request, User user, SseEmitter emitter) {
        try {
            Long scheduleId = request.getScheduleId();

            // ── Wizard continuation shortcut ──────────────────────────────────
            // When the frontend sends a "wizard step confirmed" message (e.g.
            // "Филиал создан, id=10. Теперь создай шаблон..."), we detect the
            // current wizard state from wizardCtx and jump directly to the next
            // step WITHOUT asking the AI — this prevents the AI from batching
            // multiple wizard steps ahead of time.
            if (isWizardContinuation(request)) {
                handleWizardContinuation(request, user, emitter);
                return;
            }
            // ──────────────────────────────────────────────────────────────────

            // Short transaction: load history + save user msg + build prompt
            List<ObjectNode> messages = prepareMessages(user, request, scheduleId);

            String finalAnswer = null;
            for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
                JsonNode response = callOpenAiWithTools(messages);
                JsonNode choice = response.get("choices").get(0);
                JsonNode message = choice.get("message");
                String finishReason = choice.get("finish_reason").asText();

                messages.add(messageFromAssistantNode(message));

                if ("tool_calls".equals(finishReason)) {
                    JsonNode toolCalls = message.get("tool_calls");

                    // Guard: if any tool in this batch is a wizard step, fire only the FIRST
                    // wizard tool and stop. The AI sometimes batches multiple wizard_* calls
                    // in one response despite instructions — this enforces one-step-at-a-time.
                    for (JsonNode tc : toolCalls) {
                        String tn = tc.get("function").get("name").asText();
                        if (tn.startsWith("wizard_")) {
                            JsonNode wizArgs = objectMapper.readTree(tc.get("function").get("arguments").asText());
                            log.info("Agent calling wizard tool: {} args: {}", tn, wizArgs);
                            emitter.send(SseEmitter.event().name("status").data((Object) toolActionLabel(tn)));
                            sendWizardEvent(emitter, tn, wizArgs, request);
                            return; // stop immediately — frontend drives the next step
                        }
                    }

                    for (JsonNode tc : toolCalls) {
                        String toolCallId = tc.get("id").asText();
                        String toolName = tc.get("function").get("name").asText();
                        JsonNode args = objectMapper.readTree(tc.get("function").get("arguments").asText());

                        log.info("Agent calling tool: {} args: {}", toolName, args);
                        emitter.send(SseEmitter.event().name("status").data((Object) toolActionLabel(toolName)));

                        if ("fill_schedule".equals(toolName)) {
                            long sid = args.has("scheduleId")
                                ? args.get("scheduleId").asLong()
                                : (scheduleId != null ? scheduleId : -1);
                            if (sid < 0) {
                                messages.add(toolResultMessage(toolCallId,
                                    "Error: scheduleId unknown. Ask user to open the schedule page first."));
                            } else {
                                AiFillRequest fillReq = new AiFillRequest();
                                fillReq.setScheduleId(sid);
                                fillReq.setInstructions(args.has("instructions")
                                    ? args.get("instructions").asText() : "");
                                // fillScheduleStreaming calls emitter.complete() itself
                                aiService.fillScheduleStreaming(fillReq, emitter);
                                return;
                            }
                        } else {
                            String toolResult = executeTool(toolName, args, scheduleId);
                            messages.add(toolResultMessage(toolCallId, toolResult));
                            log.info("Tool {} result: {}", toolName,
                                toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult);
                        }
                    }
                } else {
                    finalAnswer = message.has("content") && !message.get("content").isNull()
                        ? message.get("content").asText()
                        : "Done!";
                    break;
                }
            }

            if (finalAnswer == null) finalAnswer = "Done! Anything else?";

            SseTextStreamer.stream(finalAnswer, emitter, 20);
            saveMessage(user, scheduleId, "assistant", finalAnswer);
            emitter.complete();

        } catch (Exception e) {
            log.error("AI agent error", e);
            try {
                emitter.send(SseEmitter.event().name("error").data((Object) Objects.toString(e.getMessage(), "Unknown error")));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  TOOL EXECUTION
    //  All queries use JOIN FETCH — no lazy loading needed.
    // ─────────────────────────────────────────────────────────

    private String executeTool(String name, JsonNode args, Long contextScheduleId) {
        try {
            return switch (name) {
                case "get_schedule_stats"      -> toolGetScheduleStats(args, contextScheduleId);
                case "list_employees"          -> toolListEmployees(args, contextScheduleId);
                case "list_schedules"          -> toolListSchedules(args);
                // wizard_* tools are handled in the main loop — should not reach here
                default -> "Unknown tool: " + name;
            };
        } catch (Exception e) {
            log.error("Tool {} error", name, e);
            return "Error in " + name + ": " + e.getMessage();
        }
    }

    private String toolGetScheduleStats(JsonNode args, Long contextScheduleId) {
        long scheduleId = args.has("scheduleId")
            ? args.get("scheduleId").asLong()
            : (contextScheduleId != null ? contextScheduleId : -1);
        if (scheduleId < 0) return "scheduleId not specified";

        // JOIN FETCH branch — no LazyInitializationException
        Schedule s = scheduleRepository.findByIdWithBranch(scheduleId).orElse(null);
        if (s == null) return "Schedule #" + scheduleId + " not found";

        List<Employee> emps = employeeRepository
            .findAllByBranchIdAndIsActiveTrueWithBranch(s.getBranch().getId());

        StringBuilder sb = new StringBuilder();
        sb.append("Schedule #").append(scheduleId)
          .append(", branch: ").append(s.getBranch().getName())
          .append(", period: ").append(s.getMonth()).append("/").append(s.getYear())
          .append(", status: ").append(s.getStatus()).append("\n");
        sb.append("Employees (").append(emps.size()).append("):\n");
        for (Employee e : emps) {
            sb.append("- #").append(e.getId()).append(" ")
              .append(e.getLastName()).append(" ").append(e.getFirstName())
              .append(", ").append(e.getPosition()).append("\n");
        }
        return sb.toString();
    }

    private String toolListEmployees(JsonNode args, Long contextScheduleId) {
        List<Employee> emps;

        if (args.has("branchId")) {
            // LLM explicitly provided branchId
            emps = employeeRepository
                .findAllByBranchIdAndIsActiveTrueWithBranch(args.get("branchId").asLong());
        } else if (contextScheduleId != null) {
            // User is on a schedule page — use that branch automatically
            Schedule s = scheduleRepository.findByIdWithBranch(contextScheduleId).orElse(null);
            if (s != null) {
                emps = employeeRepository
                    .findAllByBranchIdAndIsActiveTrueWithBranch(s.getBranch().getId());
            } else {
                emps = employeeRepository.findAll().stream()
                    .filter(Employee::getIsActive).collect(Collectors.toList());
            }
        } else {
            emps = employeeRepository.findAll().stream()
                .filter(Employee::getIsActive).collect(Collectors.toList());
        }

        if (emps.isEmpty()) return "No employees found";
        return emps.stream()
            .map(e -> String.format("#%d %s %s, %s (branch: %s)",
                e.getId(), e.getLastName(), e.getFirstName(),
                e.getPosition(), e.getBranch().getName()))
            .collect(Collectors.joining("\n"));
    }

    private String toolListSchedules(JsonNode args) {
        // findAllWithBranch would be ideal but findAll works here because
        // branch.getId() is the FK column — loaded without lazy proxy.
        // branch.getName() triggers lazy — so we filter first, then fetch with JOIN FETCH.
        List<Schedule> schedules = scheduleRepository.findAll();

        if (args.has("branchId")) {
            long bid = args.get("branchId").asLong();
            schedules = schedules.stream()
                .filter(s -> s.getBranch().getId().equals(bid)).collect(Collectors.toList());
        }
        if (args.has("month")) {
            int m = args.get("month").asInt();
            schedules = schedules.stream()
                .filter(s -> s.getMonth() == m).collect(Collectors.toList());
        }
        if (args.has("year")) {
            int y = args.get("year").asInt();
            schedules = schedules.stream()
                .filter(s -> s.getYear() == y).collect(Collectors.toList());
        }
        if (schedules.isEmpty()) return "No schedules found";

        // Re-fetch filtered list with branch to avoid lazy init on getName()
        List<Long> ids = schedules.stream().map(Schedule::getId).collect(Collectors.toList());
        return ids.stream()
            .map(id -> scheduleRepository.findByIdWithBranch(id).orElse(null))
            .filter(Objects::nonNull)
            .map(s -> String.format("#%d %s %d/%d [%s]",
                s.getId(), s.getBranch().getName(), s.getMonth(), s.getYear(), s.getStatus()))
            .collect(Collectors.joining("\n"));
    }

    // ─────────────────────────────────────────────────────────
    //  SYSTEM PROMPT
    // ─────────────────────────────────────────────────────────

    private String buildSystemPrompt(User user, AiChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI assistant for the WorGraph HR scheduling system.\n");
        sb.append("Respond in the same language the user writes in (Russian or English).\n");
        sb.append("User: ").append(user.getUsername()).append(" (manager)\n\n");

        if (request.getScheduleId() != null) {
            // findByIdWithBranch uses JOIN FETCH — safe outside transaction
            scheduleRepository.findByIdWithBranch(request.getScheduleId()).ifPresent(s -> {
                sb.append("CURRENT PAGE CONTEXT:\n");
                sb.append("User is on schedule #").append(s.getId())
                  .append(", branch: ").append(s.getBranch().getName())
                  .append(", period: ").append(s.getMonth()).append("/").append(s.getYear())
                  .append(", status: ").append(s.getStatus()).append("\n");
                sb.append("When user says 'this schedule', 'these employees', 'fill it' — ")
                  .append("use scheduleId=").append(s.getId())
                  .append(" and branchId=").append(s.getBranch().getId())
                  .append(" automatically, do not ask.\n\n");
            });
        }

        sb.append("Rules:\n");
        sb.append("- Use tools to get real data, never make up names or numbers\n");
        sb.append("- Keep final answers short, use line breaks for lists\n");
        sb.append("- If scheduleId is needed but unknown — call list_schedules first\n");
        sb.append("- wizard_* tools show interactive UI cards to the user — always use them for creation tasks\n");
        sb.append("- wizardCtx in the request contains already-created IDs (branchId, templateId, scheduleId)\n");
        sb.append("  Use these IDs when calling subsequent wizard steps\n");
        sb.append("\n");
        sb.append("CRITICAL WIZARD RULES — FOLLOW EXACTLY:\n");
        sb.append("1. Call ONE wizard_* tool per response, then STOP. Never call two wizard_* tools in one response.\n");
        sb.append("2. After any wizard_* tool call, do NOT call another tool or add a text answer. Just stop.\n");
        sb.append("3. Wizard steps MUST go in order: branch -> template -> employees -> schedule.\n");
        sb.append("   - Call wizard_create_branch ONLY if wizardCtx has no branchId.\n");
        sb.append("   - Call wizard_create_template ONLY if wizardCtx has no templateId.\n");
        sb.append("   - Call wizard_create_employees ONLY after branch + template are confirmed.\n");
        sb.append("   - Call wizard_create_schedule ONLY if wizardCtx has both branchId and templateId.\n");
        sb.append("4. Wait for the user message after each step before proceeding to the next.\n");

        // Append wizardCtx from request if present
        if (request.getWizardCtx() != null && !request.getWizardCtx().isEmpty()) {
            sb.append("\nWIZARD CONTEXT (already created in this session):\n");
            request.getWizardCtx().forEach((k, v) ->
                sb.append("- ").append(k).append(": ").append(v).append("\n")
            );
        }

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────
    //  @Transactional HELPERS
    // ─────────────────────────────────────────────────────────

    @Transactional
    public List<ObjectNode> prepareMessages(User user, AiChatRequest request, Long scheduleId) {
        List<AiConversation> history = scheduleId != null
            ? conversationRepository.findTop20ByUserIdAndScheduleIdOrderByCreatedAtAsc(user.getId(), scheduleId)
            : conversationRepository.findTop20ByUserIdAndScheduleIdIsNullOrderByCreatedAtAsc(user.getId());

        AiConversation userConversation = AiConversation.builder()
            .user(user).scheduleId(scheduleId)
            .role("user").content(request.getMessage())
            .build();
        conversationRepository.save(userConversation);

        String systemPrompt = buildSystemPrompt(user, request);

        List<ObjectNode> messages = new ArrayList<>();

        ObjectNode sys = objectMapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        messages.add(sys);

        for (AiConversation h : history) {
            ObjectNode m = objectMapper.createObjectNode();
            m.put("role", h.getRole());
            m.put("content", h.getContent());
            messages.add(m);
        }

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", request.getMessage());
        messages.add(userMsg);

        return messages;
    }

    @Transactional
    public void saveMessage(User user, Long scheduleId, String role, String content) {
        AiConversation conversation = AiConversation.builder()
            .user(user).scheduleId(scheduleId)
            .role(role).content(content)
            .build();
        conversationRepository.save(conversation);
    }

    @Transactional
    public void clearHistory(User user, Long scheduleId) {
        if (scheduleId != null) {
            conversationRepository.deleteAllByUserIdAndScheduleId(user.getId(), scheduleId);
        } else {
            conversationRepository.deleteAllByUserId(user.getId());
        }
    }

    // ─────────────────────────────────────────────────────────
    //  OPENAI HTTP
    // ─────────────────────────────────────────────────────────

    private JsonNode callOpenAiWithTools(List<ObjectNode> messages) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "gpt-4o-mini");
        body.put("temperature", 0.3);

        ArrayNode messagesArr = objectMapper.createArrayNode();
        messages.forEach(messagesArr::add);
        body.set("messages", messagesArr);
        body.set("tools", objectMapper.valueToTree(buildToolDefinitions()));
        body.put("tool_choice", "auto");

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OPENAI_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + openAiKey)
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI error " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readTree(response.body());
    }

    // ─────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────

    private ObjectNode messageFromAssistantNode(JsonNode node) {
        ObjectNode m = objectMapper.createObjectNode();
        m.put("role", "assistant");
        if (node.has("content") && !node.get("content").isNull()) {
            m.put("content", node.get("content").asText());
        } else {
            m.putNull("content");
        }
        if (node.has("tool_calls")) {
            m.set("tool_calls", node.get("tool_calls").deepCopy());
        }
        return m;
    }

    private ObjectNode toolResultMessage(String toolCallId, String result) {
        ObjectNode m = objectMapper.createObjectNode();
        m.put("role", "tool");
        m.put("tool_call_id", toolCallId);
        m.put("content", result);
        return m;
    }

    private String toolActionLabel(String toolName) {
        return switch (toolName) {
            case "fill_schedule"      -> "Filling schedule...";
            case "get_schedule_stats" -> "Loading stats...";
            case "list_employees"     -> "Loading employees...";
            case "list_schedules"     -> "Searching schedules...";
            default -> "Working...";
        };
    }

    // ─────────────────────────────────────────────────────────
    //  WIZARD CONTINUATION — deterministic next-step routing
    //  Called when the frontend confirms a wizard step and sends
    //  the next "please do X" message.  We read wizardCtx to
    //  decide which step comes next without invoking the AI.
    // ─────────────────────────────────────────────────────────

    /**
     * Returns true when the user message looks like a wizard
     * continuation prompt sent automatically by the frontend
     * (e.g. "Филиал создан, id=10. Теперь создай шаблон...").
     * We detect this by checking wizardCtx: if it has any entry
     * the frontend is driving a wizard flow.
     */
    private boolean isWizardContinuation(AiChatRequest request) {
        return request.getWizardCtx() != null && !request.getWizardCtx().isEmpty();
    }

    /**
     * Determine the next wizard step from wizardCtx and send
     * the appropriate wizard SSE event directly, bypassing the AI.
     *
     * Expected wizardCtx keys (all optional, added as steps complete):
     *   branchId   – set after wizard_create_branch completes
     *   templateId – set after wizard_create_template completes
     *   employeesDone – set to "true" after wizard_create_employees completes
     *   scheduleId – set after wizard_create_schedule completes
     */
    private void handleWizardContinuation(AiChatRequest request, User user, SseEmitter emitter) throws Exception {
        Map<String, String> ctx = request.getWizardCtx();
        boolean hasBranch    = ctx.containsKey("branchId")   && !ctx.get("branchId").isBlank();
        boolean hasTemplate  = ctx.containsKey("templateId") && !ctx.get("templateId").isBlank();
        boolean hasEmployees = ctx.containsKey("employeesDone") && "true".equals(ctx.get("employeesDone"));
        boolean hasSchedule  = ctx.containsKey("scheduleId") && !ctx.get("scheduleId").isBlank();

        log.info("Wizard continuation — ctx: {}", ctx);

        ObjectNode args = objectMapper.createObjectNode();

        if (!hasBranch) {
            // Step 1: create branch — extract suggestion from message if possible
            args.put("name", "");
            args.put("address", "");
            sendWizardEvent(emitter, "wizard_create_branch", args, request);

        } else if (!hasTemplate) {
            // Step 2: create template
            args.put("name", "Шаблон для филиала " + ctx.get("branchId"));
            args.put("description", "");
            sendWizardEvent(emitter, "wizard_create_template", args, request);

        } else if (!hasEmployees) {
            // Step 3: add employees
            args.put("employees", "[]");
            sendWizardEvent(emitter, "wizard_create_employees", args, request);

        } else if (!hasSchedule) {
            // Step 4: create schedule
            int month = java.time.LocalDate.now().getMonthValue();
            int year  = java.time.LocalDate.now().getYear();
            args.put("month",      month);
            args.put("year",       year);
            args.put("branchId",   Long.parseLong(ctx.get("branchId")));
            args.put("templateId", Long.parseLong(ctx.get("templateId")));
            sendWizardEvent(emitter, "wizard_create_schedule", args, request);

        } else {
            // All steps done — send a completion message
            String done = "✅ Всё готово! Филиал, шаблон, сотрудники и расписание созданы.";
            SseTextStreamer.stream(done, emitter, 20);
            saveMessage(user, request.getScheduleId(), "assistant", done);
            emitter.complete();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  WIZARD SSE EVENT
    // ─────────────────────────────────────────────────────────

    private void sendWizardEvent(SseEmitter emitter, String toolName, JsonNode args, AiChatRequest request) throws Exception {
        String stepType = switch (toolName) {
            case "wizard_create_branch"    -> "branch";
            case "wizard_create_template"  -> "template";
            case "wizard_create_employees" -> "employees";
            case "wizard_create_schedule"  -> "schedule";
            default -> toolName.replace("wizard_create_", "");
        };

        String data;
        if ("employees".equals(stepType)) {
            // args.employees is a JSON string — parse and re-wrap
            String empJson = args.has("employees") ? args.get("employees").asText() : "[]";
            try {
                objectMapper.readTree(empJson); // validate
            } catch (Exception e) {
                empJson = "[]";
            }
            data = String.format("{\"type\":\"employees\",\"data\":%s}", empJson);
        } else if ("schedule".equals(stepType)) {
            int month = args.has("month") ? args.get("month").asInt() : java.time.LocalDate.now().getMonthValue();
            int year  = args.has("year")  ? args.get("year").asInt()  : java.time.LocalDate.now().getYear();
            // branchId / templateId come from wizardCtx in frontend, but we also pass them if available
            long branchId   = args.has("branchId")   ? args.get("branchId").asLong()   : 0;
            long templateId = args.has("templateId") ? args.get("templateId").asLong() : 0;
            data = String.format(
                "{\"type\":\"schedule\",\"data\":{\"month\":%d,\"year\":%d,\"branchId\":%d,\"templateId\":%d}}",
                month, year, branchId, templateId
            );
        } else {
            // branch / template — simple key-value args
            ObjectNode dataNode = objectMapper.createObjectNode();
            args.fields().forEachRemaining(e -> dataNode.put(e.getKey(), e.getValue().asText()));
            data = String.format("{\"type\":\"%s\",\"data\":%s}", stepType, objectMapper.writeValueAsString(dataNode));
        }

        emitter.send(SseEmitter.event().name("wizard").data(data));
        emitter.complete();
    }

    // ─────────────────────────────────────────────────────────
    //  JSON SCHEMA BUILDERS
    // ─────────────────────────────────────────────────────────

    private Map<String, Object> tool(String name, String description,
                                     Map<String, Object> properties, List<String> required) {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", name,
                "description", description,
                "parameters", Map.of(
                    "type", "object",
                    "properties", properties,
                    "required", required
                )
            )
        );
    }

    @SafeVarargs
    private Map<String, Object> props(Map.Entry<String, Object>... entries) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : entries) m.put(e.getKey(), e.getValue());
        return m;
    }

    private Map.Entry<String, Object> param(String name, String type, String description) {
        return Map.entry(name, Map.of("type", type, "description", description));
    }
}