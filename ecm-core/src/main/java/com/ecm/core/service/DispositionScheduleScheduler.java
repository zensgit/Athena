package com.ecm.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispositionScheduleScheduler {

    private final DispositionScheduleService dispositionScheduleService;

    @Scheduled(cron = "${ecm.disposition.schedule.cron:0 35 3 * * *}")
    public void runSchedules() {
        DispositionScheduleService.DispositionBatchExecutionDto result = dispositionScheduleService.runScheduledSchedules();
        if (result.executedSchedules() > 0) {
            log.info(
                "Disposition scheduler executed {} schedules, cutoff={}, archivedNodes={}, destroyedNodes={}, blocked={}, failures={}",
                result.executedSchedules(),
                result.cutoffCount(),
                result.archivedNodeCount(),
                result.destroyedNodeCount(),
                result.blockedCount(),
                result.failureCount()
            );
        }
    }
}
