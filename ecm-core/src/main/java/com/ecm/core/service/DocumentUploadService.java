package com.ecm.core.service;

import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessingPipeline;
import com.ecm.core.pipeline.PipelineResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Document Upload Service
 *
 * High-level service for document upload operations.
 * Delegates to the processing pipeline for actual processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    private final DocumentProcessingPipeline pipeline;
    private final SecurityService securityService;

    /**
     * Upload a document with full pipeline processing.
     *
     * @param file           The uploaded file
     * @param parentFolderId Target folder ID (optional)
     * @param properties     Custom properties (optional)
     * @return PipelineResult containing processing results
     */
    @Transactional
    public PipelineResult uploadDocument(
            MultipartFile file,
            UUID parentFolderId,
            Map<String, Object> properties) throws IOException {

        log.info("Starting document upload: {} ({} bytes) to folder: {}",
            file.getOriginalFilename(), file.getSize(), parentFolderId);

        // Build context
        DocumentContext context = DocumentContext.builder()
            .originalFilename(file.getOriginalFilename())
            .inputStream(file.getInputStream())
            .parentFolderId(parentFolderId)
            .userId(securityService.getCurrentUser())
            .build();

        // Add custom properties if provided
        if (properties != null) {
            context.getProperties().putAll(properties);
        }

        // Execute pipeline
        PipelineResult result = pipeline.execute(context);

        if (result.isSuccess()) {
            log.info("Document upload successful: {} -> {}",
                file.getOriginalFilename(), result.getDocumentId());
        } else {
            log.warn("Document upload failed: {} - errors: {}",
                file.getOriginalFilename(), result.getErrors());
        }

        return result;
    }

    /**
     * Upload multiple documents in batch.
     *
     * @param files          List of files to upload
     * @param parentFolderId Target folder ID
     * @return Map of filename to PipelineResult
     */
    @Transactional
    public Map<String, PipelineResult> uploadBatch(
            MultipartFile[] files,
            UUID parentFolderId) throws IOException {

        log.info("Starting batch upload of {} files to folder: {}", files.length, parentFolderId);

        java.util.Map<String, PipelineResult> results = new java.util.LinkedHashMap<>();

        for (MultipartFile file : files) {
            try {
                PipelineResult result = uploadDocument(file, parentFolderId, null);
                results.put(file.getOriginalFilename(), result);
            } catch (Exception e) {
                log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
                // Create error result
                PipelineResult errorResult = PipelineResult.builder()
                    .success(false)
                    .errors(Map.of("upload", e.getMessage()))
                    .build();
                results.put(file.getOriginalFilename(), errorResult);
            }
        }

        long successCount = results.values().stream().filter(PipelineResult::isSuccess).count();
        log.info("Batch upload completed: {}/{} successful", successCount, files.length);

        return results;
    }
}
