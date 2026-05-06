package com.ecm.core.service;

import com.ecm.core.config.PropertyEncryptionAsyncConfiguration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyEncryptionRewrapRunner {

    private final PropertyEncryptionOperationsService propertyEncryptionOperationsService;

    @Async(PropertyEncryptionAsyncConfiguration.PROPERTY_ENCRYPTION_REWRAP_TASK_EXECUTOR)
    public void runClaimedRewrapJob(UUID jobId, Integer batchSize, String requestedBy) {
        try {
            propertyEncryptionOperationsService.runClaimedRewrapJob(jobId, batchSize, requestedBy);
        } catch (Exception ex) {
            log.error("Property encryption rewrap async execution failed for job {}", jobId, ex);
        }
    }
}
