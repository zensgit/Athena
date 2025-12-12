package com.ecm.core.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Document Processing Pipeline
 *
 * Orchestrates the execution of document processors in order.
 * Each processor is executed based on its getOrder() value.
 *
 * Pipeline execution:
 * 1. Sort processors by order
 * 2. For each processor:
 *    a. Check if processor supports the context
 *    b. Execute processor
 *    c. Handle result (continue, skip, or stop)
 * 3. Return final result with all processor results
 */
@Slf4j
@Component
public class DocumentProcessingPipeline {

    private final List<DocumentProcessor> processors;

    public DocumentProcessingPipeline(List<DocumentProcessor> processors) {
        // Sort processors by order
        this.processors = new ArrayList<>(processors);
        this.processors.sort(Comparator.comparingInt(DocumentProcessor::getOrder));

        log.info("Initialized document processing pipeline with {} processors:", processors.size());
        for (DocumentProcessor processor : this.processors) {
            log.info("  - [{}] {} (order: {})",
                processor.getOrder(), processor.getName(), processor.getOrder());
        }
    }

    /**
     * Execute the pipeline on the given context.
     *
     * @param context The document context to process
     * @return PipelineResult containing all processor results
     */
    public PipelineResult execute(DocumentContext context) {
        long startTime = System.currentTimeMillis();
        List<ProcessorExecution> executions = new ArrayList<>();

        log.info("Starting pipeline execution for: {}", context.getOriginalFilename());

        for (DocumentProcessor processor : processors) {
            // Check if we should continue processing
            if (!context.isContinueProcessing()) {
                log.info("Pipeline stopped - no further processing");
                break;
            }

            String processorName = processor.getName();
            long processorStartTime = System.currentTimeMillis();

            try {
                // Check if processor supports this context
                if (!processor.supports(context)) {
                    log.debug("Processor {} skipped - does not support context", processorName);
                    executions.add(ProcessorExecution.builder()
                        .processorName(processorName)
                        .order(processor.getOrder())
                        .result(ProcessingResult.skipped("Not supported"))
                        .durationMs(0)
                        .build());
                    continue;
                }

                // Execute processor
                log.debug("Executing processor: {} (order: {})", processorName, processor.getOrder());
                ProcessingResult result = processor.process(context);
                long duration = System.currentTimeMillis() - processorStartTime;

                executions.add(ProcessorExecution.builder()
                    .processorName(processorName)
                    .order(processor.getOrder())
                    .result(result)
                    .durationMs(duration)
                    .build());

                // Handle result
                if (result.isFatal()) {
                    log.error("Processor {} failed fatally: {}", processorName, result.getMessage());
                    context.addError(processorName, result.getMessage());
                    context.stopProcessing();
                } else if (result.getStatus() == ProcessingResult.Status.SKIPPED) {
                    log.debug("Processor {} skipped: {}", processorName, result.getMessage());
                } else if (!result.isSuccess()) {
                    log.warn("Processor {} returned {}: {}",
                        processorName, result.getStatus(), result.getMessage());
                    context.addError(processorName, result.getMessage());
                } else {
                    log.debug("Processor {} completed successfully in {}ms", processorName, duration);
                }

            } catch (Exception e) {
                log.error("Unexpected error in processor {}: {}", processorName, e.getMessage(), e);
                long duration = System.currentTimeMillis() - processorStartTime;

                executions.add(ProcessorExecution.builder()
                    .processorName(processorName)
                    .order(processor.getOrder())
                    .result(ProcessingResult.fatal("Unexpected error: " + e.getMessage()))
                    .durationMs(duration)
                    .build());

                context.stopProcessing();
                context.addError(processorName, e.getMessage());
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;

        PipelineResult pipelineResult = PipelineResult.builder()
            .success(!context.hasErrors() && context.getDocumentId() != null)
            .documentId(context.getDocumentId())
            .contentId(context.getContentId())
            .executions(executions)
            .totalDurationMs(totalDuration)
            .errors(context.getErrors())
            .build();

        log.info("Pipeline execution completed in {}ms - success: {}, document: {}",
            totalDuration, pipelineResult.isSuccess(), context.getDocumentId());

        return pipelineResult;
    }

    /**
     * Get the list of registered processors.
     */
    public List<DocumentProcessor> getProcessors() {
        return List.copyOf(processors);
    }
}
