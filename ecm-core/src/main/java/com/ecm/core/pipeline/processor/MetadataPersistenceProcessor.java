package com.ecm.core.pipeline.processor;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node.NodeStatus;
import com.ecm.core.event.NodeCreatedEvent;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Metadata Persistence Processor (Order: 400)
 *
 * Persists the document entity to PostgreSQL (Source of Truth).
 * Creates the Document record with all extracted metadata.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataPersistenceProcessor implements DocumentProcessor {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public int getOrder() {
        return 400;
    }

    @Override
    public ProcessingResult process(DocumentContext context) {
        long startTime = System.currentTimeMillis();

        // Validate required fields
        if (context.getContentId() == null) {
            return ProcessingResult.fatal("Content ID is required for persistence");
        }

        try {
            // Create document entity
            Document document = new Document();
            document.setName(context.getOriginalFilename());
            document.setMimeType(context.getMimeType());
            document.setFileSize(context.getFileSize());
            document.setContentId(context.getContentId());
            document.setContentHash(context.getContentHash());
            document.setStatus(NodeStatus.ACTIVE);

            // Set parent folder if provided
            if (context.getParentFolderId() != null) {
                Folder parent = folderRepository.findById(context.getParentFolderId())
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Parent folder not found: " + context.getParentFolderId()));
                document.setParent(parent);
            }

            // Set extracted metadata
            if (context.getExtractedMetadata() != null) {
                document.getMetadata().putAll(context.getExtractedMetadata());
            }

            // Set extracted text for full-text search
            if (context.getExtractedText() != null) {
                document.setTextContent(context.getExtractedText());
                document.getMetadata().put("extractedText", context.getExtractedText());
            }

            // Set custom properties
            if (context.getProperties() != null) {
                document.getProperties().putAll(context.getProperties());
            }

            // Set creator
            document.setCreatedBy(context.getUserId());
            document.setLastModifiedBy(context.getUserId());

            // Save to database (Source of Truth)
            Document savedDocument = documentRepository.save(document);
            context.setDocumentId(savedDocument.getId());
            context.setDocument(savedDocument);

            // Publish event for other listeners
            eventPublisher.publishEvent(new NodeCreatedEvent(savedDocument, context.getUserId()));

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("Persisted document: {} (ID: {}) in {}ms",
                savedDocument.getName(), savedDocument.getId(), processingTime);

            return ProcessingResult.builder()
                .status(ProcessingResult.Status.SUCCESS)
                .processingTimeMs(processingTime)
                .message("Document persisted: " + savedDocument.getId())
                .build();

        } catch (Exception e) {
            log.error("Failed to persist document: {}", e.getMessage(), e);
            context.addError(getName(), e.getMessage());
            return ProcessingResult.fatal("Persistence failed: " + e.getMessage());
        }
    }
}
