package com.ecm.core.pipeline.processor;

import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.service.ContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Content Storage Processor (Order: 100)
 *
 * Stores the file content to the storage backend (MinIO/file system).
 * Sets contentId, fileSize, and contentHash in the context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentStorageProcessor implements DocumentProcessor {

    private final ContentService contentService;

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public ProcessingResult process(DocumentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            if (context.getInputStream() == null) {
                return ProcessingResult.fatal("No input stream provided");
            }

            // Store content and get content ID
            String contentId = contentService.storeContent(
                context.getInputStream(),
                context.getOriginalFilename()
            );

            context.setContentId(contentId);

            // Get file size
            long fileSize = contentService.getContentSize(contentId);
            context.setFileSize(fileSize);

            // Detect MIME type
            String mimeType = contentService.detectMimeType(contentId, context.getOriginalFilename());
            context.setMimeType(mimeType);

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("Stored content: {} ({} bytes, {}) in {}ms",
                contentId, fileSize, mimeType, processingTime);

            return ProcessingResult.builder()
                .status(ProcessingResult.Status.SUCCESS)
                .processingTimeMs(processingTime)
                .message("Content stored: " + contentId)
                .build();

        } catch (IOException e) {
            log.error("Failed to store content: {}", e.getMessage(), e);
            context.addError(getName(), e.getMessage());
            return ProcessingResult.fatal("Content storage failed: " + e.getMessage());
        }
    }
}
