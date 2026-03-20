package com.schedule.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEmployeeAccountRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 100)
        String username,

        @NotBlank(message = "Email is required")
        @Email
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6)
        String password
) {}