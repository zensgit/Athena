package com.ecm.core.asynctask;

import java.time.Instant;

public record AsyncTaskStatusSnapshot(
    String domainKey,
    String domainLabel,
    String taskId,
    String status,
    String error,
    Instant createdAt,
    Instant startedAt,
    Instant updatedAt,
    Instant timeoutAt,
    Instant expiresAt,
    Instant finishedAt,
    String filename,
    String createdBy,
    String updatedBy,
    AsyncTaskActionSnapshot actions,
    String fingerprint,
    boolean acknowledged,
    Instant acknowledgedAt
) {

    public AsyncTaskStatusSnapshot(
        String domainKey,
        String domainLabel,
        String taskId,
        String status,
        String error,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant timeoutAt,
        Instant expiresAt,
        Instant finishedAt,
        String filename,
        String createdBy,
        String updatedBy,
        AsyncTaskActionSnapshot actions
    ) {
        this(
            domainKey,
            domainLabel,
            taskId,
            status,
            error,
            createdAt,
            startedAt,
            updatedAt,
            timeoutAt,
            expiresAt,
            finishedAt,
            filename,
            createdBy,
            updatedBy,
            actions,
            null,
            false,
            null
        );
    }

    public Instant sortTimestamp() {
        if (updatedAt != null) {
            return updatedAt;
        }
        if (finishedAt != null) {
            return finishedAt;
        }
        if (startedAt != null) {
            return startedAt;
        }
        if (createdAt != null) {
            return createdAt;
        }
        return Instant.EPOCH;
    }

    public AsyncTaskStatusSnapshot withAcknowledgement(
        String fingerprint,
        boolean acknowledged,
        Instant acknowledgedAt
    ) {
        return new AsyncTaskStatusSnapshot(
            domainKey,
            domainLabel,
            taskId,
            status,
            error,
            createdAt,
            startedAt,
            updatedAt,
            timeoutAt,
            expiresAt,
            finishedAt,
            filename,
            createdBy,
            updatedBy,
            actions,
            fingerprint,
            acknowledged,
            acknowledged ? acknowledgedAt : null
        );
    }
}
