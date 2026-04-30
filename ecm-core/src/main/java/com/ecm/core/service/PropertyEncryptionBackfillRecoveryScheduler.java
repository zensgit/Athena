package com.ecm.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyEncryptionBackfillRecoveryScheduler {

    private final PropertyEncryptionOperationsService propertyEncryptionOperationsService;

    @Value("${ecm.property-encryption.backfill.recovery.enabled:true}")
    private boolean recoveryEnabled;

    @Value("${ecm.property-encryption.backfill.recovery.stale-after-minutes:360}")
    private long staleAfterMinutes;

    @Scheduled(
        fixedDelayString = "${ecm.property-encryption.backfill.recovery.fixed-delay-ms:300000}",
        initialDelayString = "${ecm.property-encryption.backfill.recovery.initial-delay-ms:300000}"
    )
    public void recoverStaleBackfillJobs() {
        if (!recoveryEnabled) {
            return;
        }
        if (staleAfterMinutes <= 0) {
            log.warn("Property encryption backfill recovery skipped because stale-after-minutes is {}", staleAfterMinutes);
            return;
        }
        PropertyEncryptionOperationsService.StaleBackfillRecoveryResult result =
            propertyEncryptionOperationsService.recoverStaleBackfillJobs(Duration.ofMinutes(staleAfterMinutes));
        if (result.recoveredCount() > 0) {
            log.warn("Property encryption backfill recovery terminal-marked {} stale active jobs", result.recoveredCount());
        }
    }
}
