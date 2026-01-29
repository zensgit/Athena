package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MailProcessedRetentionService {

    private final ProcessedMailRepository processedMailRepository;

    @Value("${ecm.mail.processed.retention-days:90}")
    private int retentionDays;

    public int getRetentionDays() {
        return retentionDays;
    }

    public boolean isRetentionEnabled() {
        return retentionDays > 0;
    }

    public long getExpiredCount() {
        if (!isRetentionEnabled()) {
            return 0;
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        return processedMailRepository.countByProcessedAtBefore(threshold);
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredProcessedMail() {
        cleanupExpired("Scheduled cleanup");
    }

    @Transactional
    public long manualCleanupExpiredProcessedMail() {
        return cleanupExpired("Manual cleanup");
    }

    private long cleanupExpired(String label) {
        if (!isRetentionEnabled()) {
            log.info("Processed mail retention is disabled (retention-days={})", retentionDays);
            return 0;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        long count = processedMailRepository.countByProcessedAtBefore(threshold);

        if (count > 0) {
            log.info("{}: removing {} processed mail record(s) older than {} days (before {})",
                label, count, retentionDays, threshold);
            processedMailRepository.deleteByProcessedAtBefore(threshold);
            log.info("{} complete", label);
        } else {
            log.debug("No processed mail records to clean up (retention-days={})", retentionDays);
        }

        return count;
    }
}
