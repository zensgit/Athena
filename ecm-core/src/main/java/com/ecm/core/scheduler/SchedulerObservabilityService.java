package com.ecm.core.scheduler;

import org.springframework.aop.support.AopUtils;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Joins the {@link SchedulerRunRegistry} (run data, from the aspect) with Spring's
 * {@link ScheduledTaskHolder} (schedule + next-run) on the stable {@link SchedulerJobIds} id.
 *
 * <p>§3A.2: {@code nextRunAt} is computed only where well-defined (cron; fixedRate from the last
 * run); fixedDelay → {@code null} (depends on completion). Any failure → {@code null} + a
 * {@code scheduleDescription}; the method never throws.
 */
@Service
public class SchedulerObservabilityService {

    private final SchedulerRunRegistry registry;
    private final ScheduledTaskHolder scheduledTaskHolder;

    public SchedulerObservabilityService(SchedulerRunRegistry registry, ScheduledTaskHolder scheduledTaskHolder) {
        this.registry = registry;
        this.scheduledTaskHolder = scheduledTaskHolder;
    }

    public List<SchedulerJobSnapshotDto> getSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, SchedulerJobSnapshotDto> byId = new LinkedHashMap<>();

        for (ScheduledTask scheduledTask : scheduledTaskHolder.getScheduledTasks()) {
            ScheduleInfo info = describe(scheduledTask, now);
            if (info == null) {
                continue;
            }
            byId.put(info.jobId(), toDto(info.jobId(), registry.get(info.jobId()), info.nextRunAt(), info.description()));
        }

        // Defensive: surface any job that recorded a run but is not (or no longer) in the holder.
        for (Map.Entry<String, SchedulerRunRegistry.RunRecord> e : registry.snapshot().entrySet()) {
            byId.computeIfAbsent(e.getKey(), k -> toDto(k, e.getValue(), null, "unknown"));
        }

        List<SchedulerJobSnapshotDto> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(SchedulerJobSnapshotDto::jobId));
        return out;
    }

    private record ScheduleInfo(String jobId, LocalDateTime nextRunAt, String description) {
    }

    private ScheduleInfo describe(ScheduledTask scheduledTask, LocalDateTime now) {
        try {
            Task task = scheduledTask.getTask();
            String jobId = jobIdFor(task.getRunnable());
            if (jobId == null) {
                return null;
            }
            if (task instanceof CronTask cronTask) {
                return new ScheduleInfo(jobId, nextCron(cronTask.getExpression(), now), "cron: " + cronTask.getExpression());
            }
            if (task instanceof FixedRateTask fixedRateTask) {
                long ms = fixedRateTask.getInterval();
                SchedulerRunRegistry.RunRecord rec = registry.get(jobId);
                LocalDateTime next = (rec != null && rec.lastRunAt() != null)
                    ? rec.lastRunAt().plus(Duration.ofMillis(ms))
                    : null;
                return new ScheduleInfo(jobId, next, "fixedRate=" + ms + "ms");
            }
            if (task instanceof FixedDelayTask fixedDelayTask) {
                // §3A.2: next depends on the previous completion → not cleanly computable → null.
                return new ScheduleInfo(jobId, null, "fixedDelay=" + fixedDelayTask.getInterval() + "ms");
            }
            return new ScheduleInfo(jobId, null, task.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static LocalDateTime nextCron(String expression, LocalDateTime now) {
        try {
            if (expression == null || !CronExpression.isValidExpression(expression)) {
                return null;
            }
            return CronExpression.parse(expression).next(now);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String jobIdFor(Runnable runnable) {
        if (runnable instanceof ScheduledMethodRunnable smr) {
            return SchedulerJobIds.of(AopUtils.getTargetClass(smr.getTarget()), smr.getMethod());
        }
        return null;
    }

    private static SchedulerJobSnapshotDto toDto(String jobId, SchedulerRunRegistry.RunRecord rec,
                                                 LocalDateTime nextRunAt, String description) {
        if (rec == null) {
            return new SchedulerJobSnapshotDto(jobId, null, null, null, null, 0L, 0L, nextRunAt, description);
        }
        return new SchedulerJobSnapshotDto(
            jobId,
            rec.lastRunAt(),
            rec.lastStatus() == null ? null : rec.lastStatus().name(),
            rec.lastDurationMs(),
            rec.lastErrorType(),
            rec.runCount(),
            rec.failCount(),
            nextRunAt,
            description
        );
    }
}
