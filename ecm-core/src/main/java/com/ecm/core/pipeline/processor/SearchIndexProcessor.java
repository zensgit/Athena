package com.ecm.core.pipeline.processor;

import com.ecm.core.entity.Document;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.search.NodeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

/**
 * Search Index Processor (Order: 500)
 *
 * Indexes the document in Elasticsearch for full-text search.
 * This is an acceleration layer - can be rebuilt from PostgreSQL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexProcessor implements DocumentProcessor {

    private final ElasticsearchOperations elasticsearchOperations;

    @Value("${ecm.search.enabled:true}")
    private boolean searchEnabled;

    @Value("${ecm.search.refresh-after-write:false}")
    private boolean refreshAfterWrite;

    @Override
    public int getOrder() {
        return 500;
    }

    @Override
    public boolean supports(DocumentContext context) {
        return searchEnabled && context.getDocumentId() != null;
    }

    @Override
    public ProcessingResult process(DocumentContext context) {
        if (!searchEnabled) {
            return ProcessingResult.skipped("Search indexing disabled");
        }

        if (context.getDocumentId() == null) {
            return ProcessingResult.skipped("Document not persisted yet");
        }

        long startTime = System.currentTimeMillis();

        try {
            NodeDocument nodeDocument = buildNodeDocument(context);

            // Add metadata fields
            if (context.getExtractedMetadata() != null) {
                Object title = context.getExtractedMetadata().get("title");
                if (title != null) {
                    nodeDocument.setTitle(title.toString());
                }

                Object author = context.getExtractedMetadata().get("author");
                if (author != null) {
                    nodeDocument.setAuthor(author.toString());
                }
            }

            // Index to Elasticsearch
            elasticsearchOperations.save(nodeDocument);
            if (refreshAfterWrite) {
                try {
                    elasticsearchOperations.indexOps(NodeDocument.class).refresh();
                } catch (Exception refreshEx) {
                    log.debug("Failed to refresh Elasticsearch index after write: {}", refreshEx.getMessage());
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("Indexed document {} in Elasticsearch in {}ms",
                context.getDocumentId(), processingTime);

            return ProcessingResult.builder()
                .status(ProcessingResult.Status.SUCCESS)
                .processingTimeMs(processingTime)
                .message("Indexed in Elasticsearch")
                .build();

        } catch (Exception e) {
            // Search indexing failure is non-fatal - data is safe in PostgreSQL
            log.warn("Failed to index document {}: {}", context.getDocumentId(), e.getMessage());
            context.addError(getName(), e.getMessage());
            return ProcessingResult.failed("Indexing failed: " + e.getMessage());
        }
    }

    private NodeDocument buildNodeDocument(DocumentContext context) {
        Document persisted = context.getDocument();
        NodeDocument doc = persisted != null
            ? NodeDocument.fromNode(persisted)
            : NodeDocument.builder()
                .id(context.getDocumentId().toString())
                .name(context.getOriginalFilename())
                .nameSort(context.getOriginalFilename() != null ? context.getOriginalFilename().toLowerCase() : null)
                .mimeType(context.getMimeType())
                .fileSize(context.getFileSize())
                .content(context.getExtractedText())
                .createdBy(context.getUserId())
                .deleted(false)
                .build();

        // Keep extracted text in the standard fields used by search.
        if (context.getExtractedText() != null) {
            doc.setTextContent(context.getExtractedText());
            doc.setContent(context.getExtractedText());
            doc.setExtractedText(context.getExtractedText());
        }

        return doc;
    }
}
