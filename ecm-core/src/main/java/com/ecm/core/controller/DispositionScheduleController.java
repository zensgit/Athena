package com.ecm.core.controller;

import com.ecm.core.service.DispositionScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Disposition Schedules", description = "Manage disposition schedules and execution history")
@PreAuthorize("hasRole('ADMIN')")
public class DispositionScheduleController {

    private final DispositionScheduleService dispositionScheduleService;

    @GetMapping("/disposition-schedules")
    @Operation(summary = "List disposition schedules")
    public ResponseEntity<List<DispositionScheduleService.DispositionScheduleDto>> listSchedules() {
        return ResponseEntity.ok(dispositionScheduleService.listSchedules());
    }

    @GetMapping("/folders/{folderId}/disposition-schedule")
    @Operation(summary = "Get disposition schedule for a folder")
    public ResponseEntity<DispositionScheduleService.DispositionScheduleDto> getSchedule(@PathVariable UUID folderId) {
        return dispositionScheduleService.getSchedule(folderId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/folders/{folderId}/disposition-schedule")
    @Operation(summary = "Create or update a disposition schedule")
    public ResponseEntity<DispositionScheduleService.DispositionScheduleDto> upsertSchedule(
        @PathVariable UUID folderId,
        @RequestBody DispositionScheduleService.DispositionScheduleUpsertRequest request
    ) {
        return ResponseEntity.ok(dispositionScheduleService.upsertSchedule(folderId, request));
    }

    @DeleteMapping("/folders/{folderId}/disposition-schedule")
    @Operation(summary = "Delete a disposition schedule")
    public ResponseEntity<Void> deleteSchedule(@PathVariable UUID folderId) {
        dispositionScheduleService.deleteSchedule(folderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/folders/{folderId}/disposition-schedule/dry-run")
    @Operation(summary = "Preview disposition candidates")
    public ResponseEntity<DispositionScheduleService.DispositionDryRunDto> dryRunSchedule(
        @PathVariable UUID folderId,
        @RequestBody(required = false) DispositionScheduleService.DispositionScheduleUpsertRequest request
    ) {
        return ResponseEntity.ok(dispositionScheduleService.dryRunSchedule(folderId, request));
    }

    @PostMapping("/folders/{folderId}/disposition-schedule/execute")
    @Operation(summary = "Execute a disposition schedule immediately")
    public ResponseEntity<DispositionScheduleService.DispositionExecutionDto> executeSchedule(@PathVariable UUID folderId) {
        return ResponseEntity.ok(dispositionScheduleService.executeSchedule(folderId));
    }

    @GetMapping("/folders/{folderId}/disposition-schedule/executions")
    @Operation(summary = "List disposition execution history for a folder")
    public ResponseEntity<Page<DispositionScheduleService.DispositionActionExecutionDto>> listExecutions(
        @PathVariable UUID folderId,
        Pageable pageable
    ) {
        return ResponseEntity.ok(dispositionScheduleService.listExecutions(folderId, pageable));
    }

    @PostMapping("/disposition-schedules/run")
    @Operation(summary = "Run all enabled disposition schedules")
    public ResponseEntity<DispositionScheduleService.DispositionBatchExecutionDto> runSchedules() {
        return ResponseEntity.ok(dispositionScheduleService.runScheduledSchedules());
    }
}
