package com.ecm.core.service;

import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

final class ScheduledRuleValidation {

    static final String DEFAULT_TIMEZONE = "UTC";
    static final int DEFAULT_MAX_ITEMS_PER_RUN = 200;
    static final int MIN_SCHEDULE_INTERVAL_MINUTES = 5;
    private static final int INTERVAL_SAMPLE_SIZE = 12;

    private ScheduledRuleValidation() {
    }

    static ValidatedSchedule validateAndBuild(String cronExpression, String timezone, Integer maxItemsPerRun) {
        CronExpression cron = parseCronExpression(cronExpression);
        ZoneId zoneId = parseZoneId(timezone);
        int normalizedMaxItemsPerRun = normalizeMaxItemsPerRun(maxItemsPerRun);
        assertMinimumInterval(cron, zoneId);

        ZonedDateTime nextExecution = cron.next(ZonedDateTime.now(zoneId));
        if (nextExecution == null) {
            throw new IllegalArgumentException("Cron expression does not produce future executions");
        }

        return new ValidatedSchedule(
            normalizeCronExpression(cronExpression),
            zoneId.getId(),
            normalizedMaxItemsPerRun,
            nextExecution.toLocalDateTime()
        );
    }

    static List<LocalDateTime> previewExecutions(String cronExpression, String timezone, int limit) {
        CronExpression cron = parseCronExpression(cronExpression);
        ZoneId zoneId = parseZoneId(timezone);
        assertMinimumInterval(cron, zoneId);

        List<LocalDateTime> executions = new ArrayList<>();
        ZonedDateTime cursor = ZonedDateTime.now(zoneId);
        for (int i = 0; i < limit; i++) {
            cursor = cron.next(cursor);
            if (cursor == null) {
                break;
            }
            executions.add(cursor.toLocalDateTime());
        }
        return executions;
    }

    static LocalDateTime computeNextRunAt(String cronExpression, String timezone, LocalDateTime reference) {
        CronExpression cron = parseCronExpression(cronExpression);
        ZoneId zoneId = parseZoneId(timezone);
        assertMinimumInterval(cron, zoneId);

        ZonedDateTime cursor = reference != null
            ? reference.atZone(zoneId)
            : ZonedDateTime.now(zoneId);
        ZonedDateTime nextExecution = cron.next(cursor);
        return nextExecution != null ? nextExecution.toLocalDateTime() : null;
    }

    static String normalizeCronExpression(String cronExpression) {
        if (cronExpression == null) {
            return null;
        }
        String normalized = cronExpression.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        return timezone.trim();
    }

    static int normalizeMaxItemsPerRun(Integer maxItemsPerRun) {
        int normalized = maxItemsPerRun != null ? maxItemsPerRun : DEFAULT_MAX_ITEMS_PER_RUN;
        if (normalized < 1) {
            throw new IllegalArgumentException("Max items per run must be at least 1");
        }
        return normalized;
    }

    static CronExpression parseCronExpression(String cronExpression) {
        String normalized = normalizeCronExpression(cronExpression);
        if (normalized == null) {
            throw new IllegalArgumentException("Cron expression is required for scheduled rules");
        }
        try {
            return CronExpression.parse(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid cron expression: " + normalized, ex);
        }
    }

    static ZoneId parseZoneId(String timezone) {
        String normalized = normalizeTimezone(timezone);
        try {
            return ZoneId.of(normalized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid timezone: " + normalized, ex);
        }
    }

    private static void assertMinimumInterval(CronExpression cron, ZoneId zoneId) {
        ZonedDateTime previous = cron.next(ZonedDateTime.now(zoneId));
        if (previous == null) {
            throw new IllegalArgumentException("Cron expression does not produce future executions");
        }

        for (int i = 0; i < INTERVAL_SAMPLE_SIZE; i++) {
            ZonedDateTime next = cron.next(previous);
            if (next == null) {
                return;
            }
            Duration gap = Duration.between(previous, next);
            if (gap.compareTo(Duration.ofMinutes(MIN_SCHEDULE_INTERVAL_MINUTES)) < 0) {
                throw new IllegalArgumentException(
                    "Scheduled rules must run at least "
                        + MIN_SCHEDULE_INTERVAL_MINUTES
                        + " minutes apart"
                );
            }
            previous = next;
        }
    }

    record ValidatedSchedule(
        String cronExpression,
        String timezone,
        int maxItemsPerRun,
        LocalDateTime nextRunAt
    ) {
    }
}
