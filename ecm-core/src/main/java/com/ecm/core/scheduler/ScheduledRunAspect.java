package com.ecm.core.scheduler;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Captures every recurring {@code @Scheduled} invocation into the {@link SchedulerRunRegistry}
 * (Day-2 scheduler-run observability). Observability-only — it does not change scheduler behaviour.
 *
 * <p>§3A.1: on failure it records the exception TYPE then <b>rethrows the original Throwable
 * unchanged</b>, so Spring's scheduled-task error handling (and any retry cadence) is unaffected.
 */
@Aspect
@Component
public class ScheduledRunAspect {

    private final SchedulerRunRegistry registry;

    public ScheduledRunAspect(SchedulerRunRegistry registry) {
        this.registry = registry;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object recordScheduledRun(ProceedingJoinPoint joinPoint) throws Throwable {
        String jobId = SchedulerJobIds.forJoinPoint(joinPoint);
        long startNanos = System.nanoTime();
        registry.recordStart(jobId);
        try {
            Object result = joinPoint.proceed();
            registry.recordSuccess(jobId, elapsedMs(startNanos));
            return result;
        } catch (Throwable t) {
            // Type only — never the message/stack (sensitive-data logging line).
            registry.recordFailure(jobId, elapsedMs(startNanos), t.getClass().getSimpleName());
            throw t;
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
