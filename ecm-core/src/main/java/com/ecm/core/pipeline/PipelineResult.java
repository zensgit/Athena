package com.ecm.core.pipeline;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Result of a complete pipeline execution.
 */
@Data
@Builder
public class PipelineResult {

    /** Whether the pipeline completed successfully */
    private boolean success;

    /** ID of the created document (if successful) */
    private UUID documentId;

    /** Content ID in storage */
    private String contentId;

    /** List of all processor executions */
    private List<ProcessorExecution> executions;

    /** Total pipeline execution time in milliseconds */
    private long totalDurationMs;

    /** Errors encountered during processing */
    private Map<String, String> errors;

    /**
     * Get the execution result for a specific processor.
     */
    public ProcessorExecution getExecution(String processorName) {
        return executions.stream()
            .filter(e -> e.getProcessorName().equals(processorName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Check if a specific processor succeeded.
     */
    public boolean processorSucceeded(String processorName) {
        ProcessorExecution execution = getExecution(processorName);
        return execution != null && execution.getResult().isSuccess();
    }

    /**
     * Get total items processed across all processors.
     */
    public int getTotalItemsProcessed() {
        return executions.stream()
            .mapToInt(e -> e.getResult().getItemsProcessed())
            .sum();
    }
}
