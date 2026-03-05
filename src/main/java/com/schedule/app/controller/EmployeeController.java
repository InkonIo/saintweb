package com.schedule.app.controller;

import com.schedule.app.dto.request.EmployeeRequest;
import com.schedule.app.dto.response.EmployeeResponse;
import com.schedule.app.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Управление сотрудниками")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Получить активных сотрудников филиала")
    public ResponseEntity<List<EmployeeResponse>> getAllByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(employeeService.getAllByBranch(branchId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить сотрудника по ID")
    public ResponseEntity<EmployeeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Создать сотрудника")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest.Create request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Обновить данные сотрудника")
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable Long id,
            @RequestBody EmployeeRequest.Update request
    ) {
        return ResponseEntity.ok(employeeService.update(id, request));
    }
}
