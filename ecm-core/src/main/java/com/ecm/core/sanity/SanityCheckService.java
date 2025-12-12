package com.ecm.core.sanity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SanityCheckService {

    private final List<SanityChecker> checkers;

    /**
     * Run all sanity checks.
     * @param fix Whether to attempt automatic fixes.
     * @return List of reports from each checker.
     */
    public List<SanityCheckReport> runAllChecks(boolean fix) {
        log.info("Starting system sanity check (fix={})...", fix);
        List<SanityCheckReport> reports = new ArrayList<>();

        for (SanityChecker checker : checkers) {
            try {
                SanityCheckReport report = checker.check(fix);
                reports.add(report);
                log.info("Check '{}' completed with status {}", checker.getName(), report.getStatus());
            } catch (Exception e) {
                log.error("Check '{}' failed execution", checker.getName(), e);
                SanityCheckReport errorReport = SanityCheckReport.builder()
                    .checkName(checker.getName())
                    .status(SanityCheckReport.Status.ERROR)
                    .build();
                errorReport.addIssue("Execution failed: " + e.getMessage());
                reports.add(errorReport);
            }
        }

        return reports;
    }

    /**
     * Scheduled weekly sanity check (report only).
     */
    @Scheduled(cron = "0 0 3 * * SUN") // Every Sunday at 3 AM
    public void scheduledSanityCheck() {
        runAllChecks(false);
    }
}
