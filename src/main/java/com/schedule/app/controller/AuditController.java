package com.schedule.app.controller;

import com.schedule.app.dto.response.AuditResponse;
import com.schedule.app.entity.AuditLog;
import com.schedule.app.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Логи действий пользователей")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('REVIEWER')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Все логи (только REVIEWER)")
    public ResponseEntity<List<AuditResponse>> getAll() {
        return ResponseEntity.ok(auditService.getAll());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Логи по пользователю")
    public ResponseEntity<List<AuditResponse>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(auditService.getByUser(userId));
    }

    @GetMapping("/schedule/{scheduleId}")
    @Operation(summary = "Логи по графику")
    public ResponseEntity<List<AuditResponse>> getBySchedule(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(auditService.getByEntity("Schedule", scheduleId));
    }
}