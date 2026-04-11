package com.ecm.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferReplicationScheduler {

    private final TransferReplicationService transferReplicationService;

    @Scheduled(cron = "${ecm.transfer.replication.scheduler-cron:0 */5 * * * *}")
    public void runScheduledDefinitionsAndRetries() {
        TransferReplicationService.ScheduledReplicationBatchDto scheduled = transferReplicationService.runScheduledDefinitions();
        TransferReplicationService.RetriedReplicationBatchDto retries = transferReplicationService.runDueRetries();
        if (scheduled.queuedDefinitions() > 0 || retries.startedRetryJobs() > 0) {
            log.info(
                "Transfer replication scheduler queued {} definitions and started {} retries",
                scheduled.queuedDefinitions(),
                retries.startedRetryJobs()
            );
        }
    }

    @Scheduled(cron = "${ecm.transfer.replication.cleanup-cron:0 20 4 * * *}")
    public void cleanupExpiredJobs() {
        TransferReplicationService.ReplicationJobRetentionCleanupDto cleanup = transferReplicationService.cleanupExpiredJobs();
        if (cleanup.deletedJobs() > 0) {
            log.info(
                "Transfer replication cleanup deleted {} jobs across {} definitions",
                cleanup.deletedJobs(),
                cleanup.affectedDefinitions()
            );
        }
    }
}
