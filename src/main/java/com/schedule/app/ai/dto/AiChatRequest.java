package com.schedule.app.ai.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AiChatRequest {
    private String message;        // сообщение от пользователя
    private Long scheduleId;       // если открыт конкретный график
    private String currentPage;    // "/schedules/10" — текущая страница фронта

    /**
     * Wizard context from frontend — IDs of entities already created
     * in the current wizard session. Example:
     * {"branchId": 5, "templateId": 12}
     */
    private Map<String, String> wizardCtx;
}