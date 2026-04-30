package com.ecm.core.controller;

import com.ecm.core.service.PropertyEncryptionBackfillRunner;
import com.ecm.core.service.PropertyEncryptionOperationsService;
import com.ecm.core.service.PropertyEncryptionOperationsService.EncryptedPropertyDefinitionSummary;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobPlanRequest;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobRunRequest;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunRequest;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunResult;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionStatus;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionRewrapDryRunRequest;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionRewrapDryRunResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/property-encryption")
@RequiredArgsConstructor
@Tag(name = "Property Encryption Operations", description = "Property encryption diagnostics and operation planning")
@PreAuthorize("hasRole('ADMIN')")
public class PropertyEncryptionOperationsController {

    private final PropertyEncryptionOperationsService propertyEncryptionOperationsService;
    private final PropertyEncryptionBackfillRunner propertyEncryptionBackfillRunner;

    @GetMapping("/status")
    @Operation(
        summary = "Get property encryption status",
        description = "Returns secret crypto configuration health and encrypted-property storage counts without exposing key material."
    )
    public ResponseEntity<PropertyEncryptionStatus> getStatus() {
        return ResponseEntity.ok(propertyEncryptionOperationsService.getStatus());
    }

    @GetMapping("/definitions")
    @Operation(
        summary = "List encrypted property definitions",
        description = "Returns encrypted model properties for admin diagnostics without reading encrypted node values."
    )
    public ResponseEntity<List<EncryptedPropertyDefinitionSummary>> listDefinitions() {
        return ResponseEntity.ok(propertyEncryptionOperationsService.listEncryptedDefinitions());
    }

    @PostMapping("/rewrap-jobs/dry-run")
    @Operation(
        summary = "Dry-run property encryption rewrap",
        description = "Estimates encrypted property values requiring rewrap to a target key version without mutating nodes or creating jobs."
    )
    public ResponseEntity<PropertyEncryptionRewrapDryRunResult> dryRunRewrap(
        @RequestBody(required = false) PropertyEncryptionRewrapDryRunRequest request
    ) {
        return ResponseEntity.ok(propertyEncryptionOperationsService.dryRunRewrap(request));
    }

    @PostMapping("/backfill-jobs/dry-run")
    @Operation(
        summary = "Dry-run property encryption backfill",
        description = "Estimates plaintext encrypted-property values that would be moved into encrypted storage without mutating nodes or creating jobs."
    )
    public ResponseEntity<PropertyEncryptionBackfillDryRunResult> dryRunBackfill(
        @RequestBody(required = false) PropertyEncryptionBackfillDryRunRequest request
    ) {
        return ResponseEntity.ok(propertyEncryptionOperationsService.dryRunBackfill(request));
    }

    @PostMapping("/backfill-jobs/plan")
    @Operation(
        summary = "Plan a property encryption backfill job",
        description = "Persists an executable backfill dry-run snapshot as a planned job without mutating nodes or starting processing."
    )
    public ResponseEntity<PropertyEncryptionBackfillJobDto> planBackfillJob(
        @RequestBody(required = false) PropertyEncryptionBackfillJobPlanRequest request,
        Authentication authentication
    ) {
        PropertyEncryptionBackfillJobDto response = propertyEncryptionOperationsService.planBackfillJob(
            request,
            authentication != null ? authentication.getName() : "system"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/backfill-jobs")
    @Operation(
        summary = "List planned property encryption backfill jobs",
        description = "Returns recent property encryption backfill job ledger rows without exposing node values."
    )
    public ResponseEntity<List<PropertyEncryptionBackfillJobDto>> listBackfillJobs(
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(propertyEncryptionOperationsService.listBackfillJobs(limit));
    }

    @GetMapping("/backfill-jobs/{jobId}")
    @Operation(
        summary = "Get a property encryption backfill job",
        description = "Returns one property encryption backfill job ledger row without exposing node values."
    )
    public ResponseEntity<PropertyEncryptionBackfillJobDto> getBackfillJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(propertyEncryptionOperationsService.getBackfillJob(jobId));
    }

    @PostMapping("/backfill-jobs/{jobId}/run")
    @Operation(
        summary = "Start a planned property encryption backfill job",
        description = "Claims a planned backfill job, starts asynchronous processing, and returns the claimed RUNNING ledger row."
    )
    public ResponseEntity<PropertyEncryptionBackfillJobDto> runBackfillJob(
        @PathVariable UUID jobId,
        @RequestBody(required = false) PropertyEncryptionBackfillJobRunRequest request,
        Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "system";
        Integer batchSize = request != null ? request.batchSize() : null;
        PropertyEncryptionBackfillJobDto response = propertyEncryptionOperationsService.claimBackfillJobForExecution(jobId);
        propertyEncryptionBackfillRunner.runClaimedBackfillJob(
            jobId,
            batchSize,
            actor
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/backfill-jobs/{jobId}/cancel")
    @Operation(
        summary = "Cancel a property encryption backfill job",
        description = "Cancels a planned job immediately or requests cancellation for a currently running job."
    )
    public ResponseEntity<PropertyEncryptionBackfillJobDto> cancelBackfillJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(propertyEncryptionOperationsService.requestBackfillJobCancel(jobId));
    }
}
