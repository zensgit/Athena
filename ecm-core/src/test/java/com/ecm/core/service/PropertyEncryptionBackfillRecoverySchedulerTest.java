package com.ecm.core.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyEncryptionBackfillRecoverySchedulerTest {

    @Mock
    private PropertyEncryptionOperationsService propertyEncryptionOperationsService;

    @Test
    @DisplayName("scheduler skips recovery when disabled")
    void schedulerSkipsRecoveryWhenDisabled() {
        PropertyEncryptionBackfillRecoveryScheduler scheduler = new PropertyEncryptionBackfillRecoveryScheduler(
            propertyEncryptionOperationsService
        );
        ReflectionTestUtils.setField(scheduler, "recoveryEnabled", false);
        ReflectionTestUtils.setField(scheduler, "staleAfterMinutes", 360L);

        scheduler.recoverStaleBackfillJobs();

        verify(propertyEncryptionOperationsService, never()).recoverStaleBackfillJobs(Duration.ofMinutes(360));
    }

    @Test
    @DisplayName("scheduler recovers stale jobs with configured threshold")
    void schedulerRecoversStaleJobsWithConfiguredThreshold() {
        PropertyEncryptionBackfillRecoveryScheduler scheduler = new PropertyEncryptionBackfillRecoveryScheduler(
            propertyEncryptionOperationsService
        );
        ReflectionTestUtils.setField(scheduler, "recoveryEnabled", true);
        ReflectionTestUtils.setField(scheduler, "staleAfterMinutes", 45L);
        when(propertyEncryptionOperationsService.recoverStaleBackfillJobs(Duration.ofMinutes(45)))
            .thenReturn(new PropertyEncryptionOperationsService.StaleBackfillRecoveryResult(1));

        scheduler.recoverStaleBackfillJobs();

        verify(propertyEncryptionOperationsService).recoverStaleBackfillJobs(Duration.ofMinutes(45));
    }
}
