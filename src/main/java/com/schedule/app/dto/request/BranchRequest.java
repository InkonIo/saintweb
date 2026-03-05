package com.schedule.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public class BranchRequest {

    public record Create(
            @NotBlank(message = "Branch name is required")
            String name,

            String address
    ) {}

    public record Update(
            @NotBlank(message = "Branch name is required")
            String name,

            String address
    ) {}
}
