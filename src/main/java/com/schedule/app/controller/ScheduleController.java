package com.schedule.app.controller;

import com.schedule.app.dto.request.ScheduleRequest;
import com.schedule.app.dto.response.ScheduleResponse;
import com.schedule.app.service.ExportService;
import com.schedule.app.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Schedules", description = "Управление графиками работы")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final com.schedule.app.service.ConflictService conflictService;
    private final ExportService exportService;
    // ───────── GET ─────────

    @GetMapping
    @Operation(summary = "Получить все графики")
    public ResponseEntity<List<ScheduleResponse.Short>> getAll() {
        return ResponseEntity.ok(scheduleService.getAll());
    }

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Получить графики по филиалу")
    public ResponseEntity<List<ScheduleResponse.Short>> getAllByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(scheduleService.getAllByBranch(branchId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить график по ID (с ячейками)")
    public ResponseEntity<ScheduleResponse.Full> getById(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.getById(id));
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "История версий графика")
    public ResponseEntity<List<ScheduleResponse.VersionResponse>> getVersionHistory(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.getVersionHistory(id));
    }

    // ───────── CREATE / EDIT ─────────

    @PostMapping
    @Operation(summary = "Создать новый график (MANAGER)")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ScheduleResponse.Full> create(@Valid @RequestBody ScheduleRequest.Create request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(request));
    }

    @PutMapping("/{id}/entries")
    @Operation(summary = "Обновить ячейки графика (MANAGER)")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ScheduleResponse.Full> updateEntries(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleRequest.UpdateEntries request
    ) {
        return ResponseEntity.ok(scheduleService.updateEntries(id, request));
    }

    // ───────── STATUS TRANSITIONS ─────────

    @PostMapping("/{id}/submit")
    @Operation(summary = "Отправить на согласование (MANAGER)")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ScheduleResponse.Short> submit(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.submitForApproval(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Утвердить график (REVIEWER)")
    @PreAuthorize("hasRole('REVIEWER')")
    public ResponseEntity<ScheduleResponse.Short> approve(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.approve(id));
    }

    @PostMapping("/{id}/revision")
    @Operation(summary = "Вернуть на доработку с комментарием (REVIEWER)")
    @PreAuthorize("hasRole('REVIEWER')")
    public ResponseEntity<ScheduleResponse.Short> sendToRevision(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleRequest.Review request
    ) {
        return ResponseEntity.ok(scheduleService.sendToRevision(id, request));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Архивировать график")
    @PreAuthorize("hasRole('REVIEWER')")
    public ResponseEntity<ScheduleResponse.Short> archive(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.archiveSchedule(id));
    }

        @GetMapping("/{id}/conflicts")
    @Operation(summary = "Проверить конфликты графика")
    public ResponseEntity<List<Map<String, Object>>> checkConflicts(@PathVariable Long id) {
        return ResponseEntity.ok(conflictService.checkConflicts(id));
    }
    @GetMapping("/{id}/export/excel")
@Operation(summary = "Экспорт графика в Excel")
public ResponseEntity<byte[]> exportExcel(@PathVariable Long id) throws Exception {
    byte[] data = exportService.exportExcel(id);
    return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=schedule-" + id + ".xlsx")
            .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .body(data);
}

@GetMapping("/{id}/export/pdf")
@Operation(summary = "Экспорт графика в PDF")
public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) throws Exception {
    byte[] data = exportService.exportPdf(id);
    return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=schedule-" + id + ".pdf")
            .header("Content-Type", "application/pdf")
            .body(data);
}
}
