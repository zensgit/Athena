package com.ecm.core.controller;

import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.service.DocumentUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Document Upload Controller
 *
 * REST API for document upload operations.
 * Uses the document processing pipeline for intelligent processing.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Document Upload", description = "Document upload and processing API")
public class UploadController {

    private final DocumentUploadService uploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document", description = "Upload a document with automatic processing")
    public ResponseEntity<UploadResponse> uploadDocument(
            @Parameter(description = "File to upload")
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Target folder ID")
            @RequestParam(value = "folderId", required = false) UUID folderId,

            @Parameter(description = "Target folder ID (alias of folderId)")
            @RequestParam(value = "parentId", required = false) UUID parentId,

            @Parameter(description = "Document description")
            @RequestParam(value = "description", required = false) String description) throws IOException {

        Map<String, Object> properties = null;
        if (description != null) {
            properties = Map.of("description", description);
        }

        UUID effectiveFolderId = folderId != null ? folderId : parentId;
        PipelineResult result = uploadService.uploadDocument(file, effectiveFolderId, properties);

        UploadResponse response = UploadResponse.builder()
            .success(result.isSuccess())
            .documentId(result.getDocumentId())
            .contentId(result.getContentId())
            .filename(file.getOriginalFilename())
            .processingTimeMs(result.getTotalDurationMs())
            .errors(result.getErrors())
            .build();

        if (result.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload multiple documents", description = "Batch upload with automatic processing")
    public ResponseEntity<BatchUploadResponse> uploadBatch(
            @Parameter(description = "Files to upload")
            @RequestParam("files") MultipartFile[] files,

            @Parameter(description = "Target folder ID")
            @RequestParam(value = "folderId", required = false) UUID folderId,

            @Parameter(description = "Target folder ID (alias of folderId)")
            @RequestParam(value = "parentId", required = false) UUID parentId) throws IOException {

        UUID effectiveFolderId = folderId != null ? folderId : parentId;
        Map<String, PipelineResult> results = uploadService.uploadBatch(files, effectiveFolderId);

        BatchUploadResponse response = BatchUploadResponse.builder()
            .totalFiles(files.length)
            .successCount((int) results.values().stream().filter(PipelineResult::isSuccess).count())
            .failureCount((int) results.values().stream().filter(r -> !r.isSuccess()).count())
            .results(results.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> UploadResponse.builder()
                        .success(e.getValue().isSuccess())
                        .documentId(e.getValue().getDocumentId())
                        .contentId(e.getValue().getContentId())
                        .filename(e.getKey())
                        .processingTimeMs(e.getValue().getTotalDurationMs())
                        .errors(e.getValue().getErrors())
                        .build()
                )))
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/pipeline/status")
    @Operation(summary = "Get pipeline status", description = "Get information about the document processing pipeline")
    public ResponseEntity<PipelineStatusResponse> getPipelineStatus() {
        // This would normally get from the pipeline service
        return ResponseEntity.ok(PipelineStatusResponse.builder()
            .status("ACTIVE")
            .processorCount(4)
            .processors(java.util.List.of(
                "ContentStorageProcessor (100)",
                "TikaTextExtractor (200)",
                "MetadataPersistenceProcessor (400)",
                "SearchIndexProcessor (500)"
            ))
            .build());
    }

    // === Response DTOs ===

    @lombok.Data
    @lombok.Builder
    public static class UploadResponse {
        private boolean success;
        private UUID documentId;
        private String contentId;
        private String filename;
        private long processingTimeMs;
        private Map<String, String> errors;
    }

    @lombok.Data
    @lombok.Builder
    public static class BatchUploadResponse {
        private int totalFiles;
        private int successCount;
        private int failureCount;
        private Map<String, UploadResponse> results;
    }

    @lombok.Data
    @lombok.Builder
    public static class PipelineStatusResponse {
        private String status;
        private int processorCount;
        private java.util.List<String> processors;
    }
}
