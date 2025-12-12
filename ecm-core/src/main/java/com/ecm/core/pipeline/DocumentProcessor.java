package com.ecm.core.pipeline;

/**
 * Document Processing Pipeline - Core Processor Interface
 *
 * Each processor handles one aspect of document processing.
 * Processors are executed in order based on getOrder() value.
 *
 * Order Guidelines:
 * - 100: MIME type detection
 * - 200: Content extraction (Tika)
 * - 300: Metadata enrichment
 * - 400: Persistence (database)
 * - 500: Search indexing
 * - 600: Event publishing
 */
public interface DocumentProcessor {

    /**
     * Process the document context.
     *
     * @param context The document processing context containing file and metadata
     * @return ProcessingResult indicating success, failure, or skip
     */
    ProcessingResult process(DocumentContext context);

    /**
     * Get the order of this processor in the pipeline.
     * Lower values execute first.
     *
     * @return the order value
     */
    int getOrder();

    /**
     * Check if this processor should be applied to the given context.
     *
     * @param context The document processing context
     * @return true if this processor should process the document
     */
    default boolean supports(DocumentContext context) {
        return true;
    }

    /**
     * Get the name of this processor for logging and monitoring.
     *
     * @return processor name
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
