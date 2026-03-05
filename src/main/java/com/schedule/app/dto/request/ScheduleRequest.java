package com.schedule.app.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public class ScheduleRequest {

    public record Create(
            @NotNull(message = "Branch ID is required")
            Long branchId,

            Long templateId,

            @NotNull(message = "Month is required")
            @Min(value = 1, message = "Month must be between 1 and 12")
            @Max(value = 12, message = "Month must be between 1 and 12")
            Integer month,

            @NotNull(message = "Year is required")
            @Min(value = 2000, message = "Year must be >= 2000")
            Integer year
    ) {}

    public record UpdateEntries(
            @NotNull(message = "Entries are required")
            List<EntryItem> entries
    ) {
        public record EntryItem(
                @NotNull Long employeeId,
                @NotNull LocalDate workDate,
                @NotBlank String shiftType
        ) {}
    }

    public record Review(
            @NotBlank(message = "Comment is required")
            String comment
    ) {}
}
