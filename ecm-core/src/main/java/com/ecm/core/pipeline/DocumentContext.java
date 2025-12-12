package com.ecm.core.pipeline;

import lombok.Builder;
import lombok.Data;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document Processing Context
 *
 * Carries all information needed during document processing pipeline.
 * Each processor can read from and write to this context.
 */
@Data
@Builder
public class DocumentContext {

    // === Input Fields (set before pipeline execution) ===

    /** Original filename provided by the user */
    private String originalFilename;

    /** Content input stream (for initial processing) */
    private InputStream inputStream;

    /** Target parent folder ID */
    private UUID parentFolderId;

    /** User performing the upload */
    private String userId;

    // === Computed Fields (set during pipeline processing) ===

    /** Content ID after storage (MinIO/file system) */
    private String contentId;

    /** Detected MIME type */
    private String mimeType;

    /** File size in bytes */
    private Long fileSize;

    /** Content hash (SHA-256) for deduplication */
    private String contentHash;

    /** Extracted text content from Tika */
    private String extractedText;

    /** Extracted metadata from Tika */
    @Builder.Default
    private Map<String, Object> extractedMetadata = new HashMap<>();

    /** Custom properties to be stored */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    /** Suggested tags from processors (e.g., barcode, ML) */
    @Builder.Default
    private List<String> suggestedTags = new ArrayList<>();

    /** Suggested category from ML classification */
    private String suggestedCategory;

    /** Resolved JPA document entity after persistence */
    private com.ecm.core.entity.Document document;

    /** Content stream for downstream processors */
    private InputStream contentStream;

    /** Document ID after persistence */
    private UUID documentId;

    /** Version label if versioning is enabled */
    private String versionLabel;

    // === Pipeline State ===

    /** Flag to indicate if pipeline should continue */
    @Builder.Default
    private boolean continueProcessing = true;

    /** Errors collected during processing */
    @Builder.Default
    private Map<String, String> errors = new HashMap<>();

    /**
     * Add an error to the context.
     */
    public void addError(String processor, String message) {
        errors.put(processor, message);
    }

    /**
     * Check if context has any errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Stop the pipeline from further processing.
     */
    public void stopProcessing() {
        this.continueProcessing = false;
    }

    /**
     * Add extracted metadata.
     */
    public void addMetadata(String key, Object value) {
        if (value != null) {
            extractedMetadata.put(key, value);
        }
    }

    /**
     * Add custom property.
     */
    public void addProperty(String key, Object value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    /**
        * Provide a content stream; fall back to original input if not set.
        */
    public InputStream getContentStream() {
        return contentStream != null ? contentStream : inputStream;
    }
}
