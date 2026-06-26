package com.ecm.core.controller;

import com.ecm.core.queuebacklog.QueueBacklogObservabilityService;
import com.ecm.core.queuebacklog.QueueBacklogSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Queue Backlog Admin", description = "Read-only OCR / mail / transfer queue backlog observability")
public class QueueBacklogAdminController {

    private final QueueBacklogObservabilityService queueBacklogObservabilityService;

    @GetMapping({"/api/admin/queue-backlog", "/api/v1/admin/queue-backlog"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "OCR depth/oldest, mail fetch-health, and transfer pending/running/failed/oldest/stuck")
    public ResponseEntity<QueueBacklogSummaryDto> getQueueBacklog() {
        return ResponseEntity.ok(queueBacklogObservabilityService.getSummary());
    }
}
