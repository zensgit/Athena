package com.ecm.core.pipeline;

import lombok.Builder;
import lombok.Data;

/**
 * Record of a single processor execution within the pipeline.
 */
@Data
@Builder
public class ProcessorExecution {

    /** Name of the processor */
    private String processorName;

    /** Order of the processor in the pipeline */
    private int order;

    /** Result of the processor execution */
    private ProcessingResult result;

    /** Execution duration in milliseconds */
    private long durationMs;
}
