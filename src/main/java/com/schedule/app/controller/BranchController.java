package com.schedule.app.controller;
import com.schedule.app.dto.request.BranchRequest;
import com.schedule.app.dto.response.BranchResponse;
import com.schedule.app.service.BranchService;
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
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
@Tag(name = "Branches", description = "Управление филиалами")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {
    private final BranchService branchService;
    @GetMapping
    @Operation(summary = "Получить все филиалы")
    public ResponseEntity<List<BranchResponse>> getAll() {
        return ResponseEntity.ok(branchService.getAll());
    }
    @GetMapping("/{id}")
    @Operation(summary = "Получить филиал по ID")
    public ResponseEntity<BranchResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(branchService.getById(id));
    }
    @PostMapping
    @Operation(summary = "Создать филиал")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<BranchResponse> create(@Valid @RequestBody BranchRequest.Create request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchService.create(request));
    }
    @PutMapping("/{id}")
    @Operation(summary = "Обновить филиал")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<BranchResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody BranchRequest.Update request
    ) {
        return ResponseEntity.ok(branchService.update(id, request));
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить филиал")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        branchService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
