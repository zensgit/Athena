package com.ecm.core.controller;

import com.ecm.core.scheduler.SchedulerJobSnapshotDto;
import com.ecm.core.scheduler.SchedulerObservabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Scheduler Admin", description = "Recurring @Scheduled job run observability (read-only)")
public class SchedulerAdminController {

    private final SchedulerObservabilityService schedulerObservabilityService;

    @GetMapping({"/api/admin/schedulers", "/api/v1/admin/schedulers"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List recurring @Scheduled jobs with last-run / status / duration / next-run")
    public ResponseEntity<List<SchedulerJobSnapshotDto>> getSchedulers() {
        return ResponseEntity.ok(schedulerObservabilityService.getSnapshot());
    }
}
