package com.schedule.app.controller;

import com.schedule.app.entity.ScheduleEntry;
import com.schedule.app.entity.User;
import com.schedule.app.service.MyScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/my")
@RequiredArgsConstructor
@Tag(name = "My", description = "Личный кабинет сотрудника")
public class MyScheduleController {

    private final MyScheduleService myScheduleService;

        @GetMapping("/schedule")
@PreAuthorize("hasRole('EMPLOYEE')")
@Operation(summary = "Мой график")
public ResponseEntity<List<Map<String, Object>>> getMySchedule(
        @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(myScheduleService.getMySchedules(user));
}

        @GetMapping("/analytics")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Моя аналитика")
    public ResponseEntity<Map<String, Object>> getMyAnalytics(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(myScheduleService.getMyAnalytics(user));
    }
}