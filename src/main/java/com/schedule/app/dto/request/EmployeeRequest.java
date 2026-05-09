package com.schedule.app.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class EmployeeRequest {

    public record Create(
            @NotNull(message = "Branch ID is required")
            Long branchId,

            Long userId,

            @NotBlank(message = "First name is required")
            String firstName,

            @NotBlank(message = "Last name is required")
            String lastName,

            String position
    ) {}

    public record Update(
            Long userId,
            String firstName,
            String lastName,
            String position,
            Boolean isActive
    ) {}

        @Data
        public static class Batch {
            private List<Create> employees;
            private Long branchId;
        }
}
