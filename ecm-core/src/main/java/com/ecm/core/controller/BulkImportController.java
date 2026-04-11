package com.ecm.core.controller;

import com.ecm.core.entity.ImportJob.ConflictPolicy;
import com.ecm.core.service.BulkImportService;
import com.ecm.core.service.TenantQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/bulk-import", "/api/v1/bulk-import"})
@Tag(name = "Bulk Import", description = "Bulk import jobs and status")
public class BulkImportController {

    private final BulkImportService bulkImportService;
    private final TenantQuotaService tenantQuotaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Start a bulk import job")
    public ResponseEntity<BulkImportService.ImportJobDto> startImport(
        @RequestParam("files") MultipartFile[] files,
        @RequestParam(value = "relativePaths", required = false) List<String> relativePaths,
        @RequestParam(value = "targetFolderId", required = false) UUID targetFolderId,
        @RequestParam(value = "conflictPolicy", defaultValue = "SKIP") ConflictPolicy conflictPolicy
    ) throws IOException {
        if (files != null && files.length > 0) {
            long requestedBytes = Arrays.stream(files)
                .filter(Objects::nonNull)
                .mapToLong(MultipartFile::getSize)
                .sum();
            tenantQuotaService.assertQuotaAvailable(requestedBytes);
        }
        return ResponseEntity.accepted()
            .body(bulkImportService.startImport(files, relativePaths, targetFolderId, conflictPolicy));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get a bulk import job")
    public ResponseEntity<BulkImportService.ImportJobDto> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(bulkImportService.getJob(jobId));
    }

    @GetMapping
    @Operation(summary = "List bulk import jobs")
    public ResponseEntity<Page<BulkImportService.ImportJobDto>> listJobs(Pageable pageable) {
        return ResponseEntity.ok(bulkImportService.listJobs(pageable));
    }

    @DeleteMapping("/{jobId}")
    @Operation(summary = "Cancel a bulk import job")
    public ResponseEntity<BulkImportService.ImportJobDto> cancelJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(bulkImportService.cancelImport(jobId));
    }
}
