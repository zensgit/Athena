package com.ecm.core.controller;

import com.ecm.core.failureinventory.FailureInventoryService;
import com.ecm.core.failureinventory.FailureInventorySummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only ADMIN endpoint for the cross-subsystem failure inventory (taskbook §4 first-cut).
 *
 * <p>Mirrors {@code QueueBacklogAdminController}: dual-path mapping (both {@code /api/admin/...} and
 * {@code /api/v1/admin/...}), method-level {@code hasRole('ADMIN')}, returns the read-only summary DTO.
 * No write actions — replay / clear / retry / requeue live in the per-domain control planes (§5 boundary).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Failure Inventory Admin", description = "Read-only cross-subsystem failure / dead-letter inventory")
public class FailureInventoryAdminController {

    private final FailureInventoryService failureInventoryService;

    @GetMapping({"/api/admin/failure-inventory", "/api/v1/admin/failure-inventory"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cross-subsystem failure inventory",
        description = "Preview dead-letter count (+ category tally) plus reused transfer FAILED / mail ERROR counts. "
            + "Count/timestamp/type only — raw failure text stays in the deep ADMIN-gated surfaces.")
    public ResponseEntity<FailureInventorySummaryDto> getFailureInventory() {
        return ResponseEntity.ok(failureInventoryService.getSummary());
    }
}
