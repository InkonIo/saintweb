package com.schedule.app.dto.response;

import com.schedule.app.enums.AuditAction;
import java.time.LocalDateTime;

public record AuditResponse(
    Long id,
    String username,
    String userRole,
    AuditAction action,
    String entityType,
    Long entityId,
    String details,
    String ipAddress,
    LocalDateTime createdAt
) {}