package com.schedule.app.ai.dto;

import lombok.Data;

@Data
public class AiChatRequest {
    private String message;        // сообщение от пользователя
    private Long scheduleId;       // если открыт конкретный график
    private String currentPage;    // "/schedules/10" — текущая страница фронта
}