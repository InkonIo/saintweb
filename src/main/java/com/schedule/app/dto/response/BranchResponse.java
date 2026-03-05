package com.schedule.app.dto.response;

import java.time.LocalDateTime;

public record BranchResponse(
        Long id,
        String name,
        String address,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
