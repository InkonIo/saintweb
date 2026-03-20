package com.schedule.app.controller;

import com.schedule.app.dto.request.CreateEmployeeAccountRequest;
import com.schedule.app.dto.response.AuthResponse;
import com.schedule.app.service.EmployeeAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Управление сотрудниками")
public class EmployeeAccountController {

    private final EmployeeAccountService employeeAccountService;

    @PostMapping("/{employeeId}/create-account")
    @PreAuthorize("hasAnyRole('MANAGER', 'REVIEWER')")
    @Operation(summary = "Создать аккаунт для сотрудника")
    public ResponseEntity<AuthResponse.TokenResponse> createAccount(
            @PathVariable Long employeeId,
            @Valid @RequestBody CreateEmployeeAccountRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeAccountService.createAccount(employeeId, request));
    }
}