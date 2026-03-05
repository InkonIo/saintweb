package com.schedule.app.controller;

import com.schedule.app.dto.request.AuthRequest;
import com.schedule.app.dto.response.AuthResponse;
import com.schedule.app.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация и авторизация")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя")
    public ResponseEntity<AuthResponse.TokenResponse> register(
            @Valid @RequestBody AuthRequest.Register request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Вход в систему, получение JWT токена")
    public ResponseEntity<AuthResponse.TokenResponse> login(
            @Valid @RequestBody AuthRequest.Login request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }
}
