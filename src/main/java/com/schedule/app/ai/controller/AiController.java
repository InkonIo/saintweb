package com.schedule.app.ai.controller;

import com.schedule.app.ai.dto.AiChatRequest;
import com.schedule.app.ai.dto.AiFillRequest;
import com.schedule.app.ai.service.AiChatService;
import com.schedule.app.ai.service.AiService;
import com.schedule.app.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI ассистент")
public class AiController {

    private final AiChatService aiChatService;
    private final AiService aiService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Умный чат AI с историей (SSE)")
    public SseEmitter chat(
            @RequestBody AiChatRequest request,
            @AuthenticationPrincipal User user) {

        SseEmitter emitter = new SseEmitter(180_000L);
        executor.submit(() -> aiChatService.chat(request, user, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());
        return emitter;
    }

    @PostMapping(value = "/fill-schedule", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Заполнить график через AI (SSE)")
    public SseEmitter fillSchedule(@RequestBody AiFillRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        executor.submit(() -> aiService.fillScheduleStreaming(request, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());
        return emitter;
    }

    // ── FIX #4: scheduleId как query param — очищаем только историю нужного графика ──
    @DeleteMapping("/history")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Очистить историю чата. scheduleId — опционально, если не передан — чистится вся история")
    public ResponseEntity<Void> clearHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long scheduleId) {
        aiChatService.clearHistory(user, scheduleId);
        return ResponseEntity.noContent().build();
    }
}