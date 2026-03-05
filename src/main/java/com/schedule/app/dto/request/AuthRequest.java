package com.schedule.app.dto.request;

import com.schedule.app.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AuthRequest {

    public record Register(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 100, message = "Username must be 3-100 characters")
            String username,

            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 6, message = "Password must be at least 6 characters")
            String password,

            @NotNull(message = "Role is required")
            UserRole role
    ) {}

    public record Login(
            @NotBlank(message = "Username is required")
            String username,

            @NotBlank(message = "Password is required")
            String password
    ) {}
}
