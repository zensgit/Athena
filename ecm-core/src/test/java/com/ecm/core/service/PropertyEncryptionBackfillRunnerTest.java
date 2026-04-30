package com.ecm.core.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PropertyEncryptionBackfillRunnerTest {

    @Mock
    private PropertyEncryptionOperationsService propertyEncryptionOperationsService;

    @Test
    @DisplayName("runner delegates to claimed backfill execution")
    void runnerDelegatesToClaimedBackfillExecution() {
        UUID jobId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PropertyEncryptionBackfillRunner runner = new PropertyEncryptionBackfillRunner(
            propertyEncryptionOperationsService
        );

        runner.runClaimedBackfillJob(jobId, 25, "admin");

        verify(propertyEncryptionOperationsService).runClaimedBackfillJob(jobId, 25, "admin");
    }

    @Test
    @DisplayName("runner does not propagate background execution failures")
    void runnerDoesNotPropagateBackgroundExecutionFailures() {
        UUID jobId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PropertyEncryptionBackfillRunner runner = new PropertyEncryptionBackfillRunner(
            propertyEncryptionOperationsService
        );
        doThrow(new IllegalStateException("terminal update failed"))
            .when(propertyEncryptionOperationsService)
            .runClaimedBackfillJob(jobId, 25, "admin");

        assertDoesNotThrow(() -> runner.runClaimedBackfillJob(jobId, 25, "admin"));
    }
}
