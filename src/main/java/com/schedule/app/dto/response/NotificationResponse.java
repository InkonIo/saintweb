package com.schedule.app.dto.response;

import com.schedule.app.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    NotificationType type,
    String message,
    Boolean isRead,
    LocalDateTime createdAt,
    ScheduleRef schedule
) {
    public record ScheduleRef(Long id) {}
}