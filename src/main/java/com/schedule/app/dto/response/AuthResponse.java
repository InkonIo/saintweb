package com.schedule.app.dto.response;

import com.schedule.app.enums.UserRole;

public class AuthResponse {

    public record TokenResponse(
            String token,
            String username,
            String email,
            UserRole role
    ) {}

    public record UserResponse(
            Long id,
            String username,
            String email,
            UserRole role
    ) {}
}
