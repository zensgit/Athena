package com.ecm.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArchivePolicyScheduler {

    private final ArchivePolicyService archivePolicyService;

    @Scheduled(cron = "${ecm.archive.policy.cron:0 15 3 * * *}")
    public void runPolicies() {
        ArchivePolicyService.ArchivePolicyBatchExecutionDto result = archivePolicyService.runScheduledPolicies();
        if (result.executedPolicies() > 0) {
            log.info(
                "Archive policy scheduler executed {} policies, candidates={}, archivedNodes={}, failures={}",
                result.executedPolicies(),
                result.totalCandidates(),
                result.archivedNodeCount(),
                result.failureCount()
            );
        }
    }
}
