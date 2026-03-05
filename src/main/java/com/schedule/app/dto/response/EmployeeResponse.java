package com.schedule.app.dto.response;

import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        Long branchId,
        String branchName,
        Long userId,
        String firstName,
        String lastName,
        String position,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
