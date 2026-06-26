package com.ecm.core.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Acceptance for §3A.1 (capture proven by a REAL scheduled tick, not a direct call; failure rethrows)
 * and §3A.2 (next-run honesty). A real Spring scheduler fires the test {@code @Scheduled} methods; the
 * aspect (via {@code @EnableAspectJAutoProxy}) records into the registry.
 */
@SpringJUnitConfig(ScheduledRunObservabilityIntegrationTest.Config.class)
@DisplayName("Scheduler-run observability — real scheduled-tick capture (§3A.1) + next-run honesty (§3A.2)")
class ScheduledRunObservabilityIntegrationTest {

    static final List<Throwable> SCHEDULER_ERRORS = new CopyOnWriteArrayList<>();

    @EnableScheduling
    @EnableAspectJAutoProxy
    @Configuration
    static class Config {
        @Bean SchedulerRunRegistry schedulerRunRegistry() {
            return new SchedulerRunRegistry();
        }

        @Bean ScheduledRunAspect scheduledRunAspect(SchedulerRunRegistry registry) {
            return new ScheduledRunAspect(registry);
        }

        @Bean SchedulerObservabilityService schedulerObservabilityService(SchedulerRunRegistry registry, ScheduledTaskHolder holder) {
            return new SchedulerObservabilityService(registry, holder);
        }

        @Bean SucceedingJob succeedingJob() {
            return new SucceedingJob();
        }

        @Bean FailingJob failingJob() {
            return new FailingJob();
        }

        @Bean CronJob cronJob() {
            return new CronJob();
        }

        @Bean TaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(3);
            // Capture rethrown exceptions to prove the aspect did not swallow them.
            scheduler.setErrorHandler(SCHEDULER_ERRORS::add);
            scheduler.initialize();
            return scheduler;
        }
    }

    static class SucceedingJob {
        @Scheduled(fixedDelay = 40)
        public void run() {
            // succeeds
        }
    }

    static class FailingJob {
        @Scheduled(fixedDelay = 40)
        public void run() {
            throw new IllegalStateException("boom-SENSITIVE-detail");
        }
    }

    static class CronJob {
        @Scheduled(cron = "0 0 3 * * *")
        public void nightly() {
            // computable next-run; not fired during the test
        }
    }

    @Autowired SchedulerRunRegistry registry;
    @Autowired SchedulerObservabilityService service;

    @Test
    @DisplayName("§3A.1: real scheduler tick captured (SUCCESS/FAILED, type-only) and failure rethrows to Spring's error handler")
    void realTickCapturedAndRethrown() throws Exception {
        String successId = SchedulerJobIds.of(SucceedingJob.class, SucceedingJob.class.getMethod("run"));
        String failId = SchedulerJobIds.of(FailingJob.class, FailingJob.class.getMethod("run"));

        awaitStatus(successId, SchedulerRunRegistry.Status.SUCCESS);
        awaitStatus(failId, SchedulerRunRegistry.Status.FAILED);

        SchedulerRunRegistry.RunRecord bad = registry.get(failId);
        assertEquals("IllegalStateException", bad.lastErrorType(), "type only — class simple name");
        assertFalse(bad.lastErrorType().contains("SENSITIVE"), "no exception message/detail recorded");
        assertTrue(bad.failCount() >= 1);
        assertNotNull(bad.lastDurationMs());
        assertTrue(registry.get(successId).runCount() >= 1);

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
            && SCHEDULER_ERRORS.stream().noneMatch(t -> t instanceof IllegalStateException)) {
            Thread.sleep(50);
        }
        assertTrue(SCHEDULER_ERRORS.stream().anyMatch(t -> t instanceof IllegalStateException),
            "aspect must rethrow so Spring's scheduled error handling sees the original exception unchanged");
    }

    @Test
    @DisplayName("§3A.2: cron -> computed nextRunAt; fixedDelay -> null + scheduleDescription; never fabricated; service never throws")
    void nextRunHonesty() throws Exception {
        String successId = SchedulerJobIds.of(SucceedingJob.class, SucceedingJob.class.getMethod("run"));
        awaitStatus(successId, SchedulerRunRegistry.Status.SUCCESS);

        List<SchedulerJobSnapshotDto> snapshot = service.getSnapshot();
        assertFalse(snapshot.isEmpty());
        assertTrue(snapshot.stream().allMatch(s -> s.scheduleDescription() != null),
            "scheduleDescription is always non-null");

        SchedulerJobSnapshotDto cron = snapshot.stream()
            .filter(s -> s.jobId().endsWith("#nightly")).findFirst().orElseThrow();
        assertTrue(cron.scheduleDescription().startsWith("cron:"));
        assertNotNull(cron.nextRunAt(), "cron next-run is cleanly computable");

        SchedulerJobSnapshotDto fixedDelay = snapshot.stream()
            .filter(s -> s.jobId().equals(successId)).findFirst().orElseThrow();
        assertTrue(fixedDelay.scheduleDescription().startsWith("fixedDelay="));
        assertNull(fixedDelay.nextRunAt(), "fixedDelay next-run is not fabricated");
    }

    private void awaitStatus(String jobId, SchedulerRunRegistry.Status expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            SchedulerRunRegistry.RunRecord record = registry.get(jobId);
            if (record != null && record.lastStatus() == expected && record.runCount() >= 1) {
                return;
            }
            Thread.sleep(50);
        }
        fail("job " + jobId + " did not reach " + expected + " via a real scheduled tick");
    }
}
