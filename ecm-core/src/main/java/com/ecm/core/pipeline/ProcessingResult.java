package com.ecm.core.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of a document processor execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingResult {

    /** Processing status */
    private Status status;

    /** Optional message (for errors or information) */
    private String message;

    /** Processing time in milliseconds */
    private long processingTimeMs;

    /** Number of items processed (e.g., pages, characters) */
    private int itemsProcessed;

    /** Arbitrary data returned by processors */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    public enum Status {
        /** Processing completed successfully */
        SUCCESS,

        /** Processing skipped (processor doesn't apply) */
        SKIPPED,

        /** Processing failed but pipeline can continue */
        FAILED,

        /** Processing failed and pipeline should stop */
        FATAL
    }

    // === Factory Methods ===

    public static ProcessingResult success() {
        return ProcessingResult.builder()
            .status(Status.SUCCESS)
            .build();
    }

    public static ProcessingResult success(String message) {
        return ProcessingResult.builder()
            .status(Status.SUCCESS)
            .message(message)
            .build();
    }

    public static ProcessingResult success(int itemsProcessed) {
        return ProcessingResult.builder()
            .status(Status.SUCCESS)
            .itemsProcessed(itemsProcessed)
            .build();
    }

    public static ProcessingResult skipped(String reason) {
        return ProcessingResult.builder()
            .status(Status.SKIPPED)
            .message(reason)
            .build();
    }

    public static ProcessingResult failed(String message) {
        return ProcessingResult.builder()
            .status(Status.FAILED)
            .message(message)
            .build();
    }

    public static ProcessingResult fatal(String message) {
        return ProcessingResult.builder()
            .status(Status.FATAL)
            .message(message)
            .build();
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFatal() {
        return status == Status.FATAL;
    }

    public boolean shouldContinue() {
        return status != Status.FATAL;
    }

    public ProcessingResult withData(String key, Object value) {
        if (data == null) {
            data = new HashMap<>();
        }
        data.put(key, value);
        return this;
    }
}
