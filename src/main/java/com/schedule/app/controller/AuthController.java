package com.schedule.app.controller;

import com.schedule.app.dto.request.AuthRequest;
import com.schedule.app.dto.response.AuthResponse;
import com.schedule.app.service.AuthService;
import com.schedule.app.service.PasswordResetService;

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
    private final PasswordResetService passwordResetService;

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

    @PostMapping("/forgot-password")
    @Operation(summary = "Отправить код на email")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody AuthRequest.ForgotPassword request
    ) {
        passwordResetService.sendResetCode(request.email());
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/verify-code")
    @Operation(summary = "Проверить код и получить reset-токен")
    public ResponseEntity<AuthResponse.TokenResponse> verifyCode(
            @Valid @RequestBody AuthRequest.VerifyCode request
    ) {
        String resetToken = passwordResetService.verifyCode(request.email(), request.code());
        return ResponseEntity.ok(new AuthResponse.TokenResponse(resetToken, null, null, null));
    }
    
    @PostMapping("/reset-password")
    @Operation(summary = "Установить новый пароль")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody AuthRequest.ResetPassword request
    ) {
        passwordResetService.resetPassword(request.resetToken(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
