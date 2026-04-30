package com.ecm.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyEncryptionBackfillRunner {

    private final PropertyEncryptionOperationsService propertyEncryptionOperationsService;

    @Async
    public void runClaimedBackfillJob(UUID jobId, Integer batchSize, String requestedBy) {
        try {
            propertyEncryptionOperationsService.runClaimedBackfillJob(jobId, batchSize, requestedBy);
        } catch (Exception ex) {
            log.error("Property encryption backfill async execution failed for job {}", jobId, ex);
        }
    }
}
