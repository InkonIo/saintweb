package com.schedule.app.dto.response;

import com.schedule.app.enums.ScheduleStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ScheduleResponse {

    public record Short(
            Long id,
            Long branchId,
            String branchName,
            Integer month,
            Integer year,
            ScheduleStatus status,
            Integer version,
            String authorUsername,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record Full(
            Long id,
            Long branchId,
            String branchName,
            Long templateId,
            String templateName,
            Integer month,
            Integer year,
            ScheduleStatus status,
            Integer version,
            String authorUsername,
            List<EntryResponse> entries,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record EntryResponse(
            Long id,
            Long employeeId,
            String employeeFirstName,
            String employeeLastName,
            LocalDate workDate,
            String shiftType
    ) {}

    public record VersionResponse(
            Long id,
            Integer versionNumber,
            String changedByUsername,
            LocalDateTime changedAt,
            ScheduleStatus status,
            String comment
    ) {}
}
