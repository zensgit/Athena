package com.ecm.core.asynctask;

public record AsyncTaskActionSnapshot(
    String cancelUrl,
    String downloadUrl,
    String cleanupUrl,
    boolean cancellable,
    boolean cleanupEligible,
    boolean downloadReady
) {}
