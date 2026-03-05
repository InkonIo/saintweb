package com.schedule.app.controller;
import com.schedule.app.entity.ScheduleTemplate;
import com.schedule.app.exception.ResourceNotFoundException;
import com.schedule.app.repository.ScheduleTemplateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Tag(name = "Schedule Templates", description = "Шаблоны графиков")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleTemplateController {
    private final ScheduleTemplateRepository templateRepository;
    @GetMapping
    @Operation(summary = "Получить все шаблоны")
    public ResponseEntity<List<ScheduleTemplate>> getAll() {
        return ResponseEntity.ok(templateRepository.findAll());
    }
    @PostMapping
    @Operation(summary = "Создать шаблон")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ScheduleTemplate> create(@RequestBody ScheduleTemplate template) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateRepository.save(template));
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить шаблон")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));
        templateRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
