package com.ecm.core.preview;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CadRenderFailoverTracker {

    private final Map<String, EndpointMutableStats> stats = new ConcurrentHashMap<>();

    private volatile boolean circuitBreakerEnabled = true;
    private volatile int circuitFailureThreshold = 3;
    private volatile long circuitOpenMs = 120000L;
    private volatile long halfOpenTrialTimeoutMs = 30000L;

    @Value("${ecm.preview.cad.circuit-breaker.enabled:true}")
    public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    @Value("${ecm.preview.cad.circuit-breaker.failure-threshold:3}")
    public void setCircuitFailureThreshold(int circuitFailureThreshold) {
        this.circuitFailureThreshold = Math.max(1, circuitFailureThreshold);
    }

    @Value("${ecm.preview.cad.circuit-breaker.open-ms:120000}")
    public void setCircuitOpenMs(long circuitOpenMs) {
        this.circuitOpenMs = Math.max(1000L, circuitOpenMs);
    }

    @Value("${ecm.preview.cad.circuit-breaker.half-open-timeout-ms:30000}")
    public void setHalfOpenTrialTimeoutMs(long halfOpenTrialTimeoutMs) {
        this.halfOpenTrialTimeoutMs = Math.max(1000L, halfOpenTrialTimeoutMs);
    }

    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    public int getCircuitFailureThreshold() {
        return circuitFailureThreshold;
    }

    public long getCircuitOpenMs() {
        return circuitOpenMs;
    }

    public long getHalfOpenTrialTimeoutMs() {
        return halfOpenTrialTimeoutMs;
    }

    public EndpointAttemptDecision beforeAttempt(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return new EndpointAttemptDecision(false, false, "INVALID_ENDPOINT", null);
        }
        EndpointMutableStats target = stats.computeIfAbsent(endpoint, ignored -> new EndpointMutableStats());
        if (!circuitBreakerEnabled) {
            return new EndpointAttemptDecision(true, false, "DISABLED", null);
        }
        synchronized (target) {
            Instant now = Instant.now();
            cleanupStaleHalfOpen(target, now);
            if (target.circuitOpenUntil != null && target.circuitOpenUntil.isAfter(now)) {
                return new EndpointAttemptDecision(false, false, "OPEN", target.circuitOpenUntil);
            }
            if (target.circuitOpenUntil != null && !target.circuitOpenUntil.isAfter(now)) {
                if (target.halfOpenInFlight) {
                    return new EndpointAttemptDecision(false, true, "HALF_OPEN_IN_FLIGHT", target.circuitOpenUntil);
                }
                target.halfOpenInFlight = true;
                target.halfOpenSince = now;
                return new EndpointAttemptDecision(true, true, "HALF_OPEN", null);
            }
            return new EndpointAttemptDecision(true, false, "CLOSED", null);
        }
    }

    public void recordSuccess(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        EndpointMutableStats target = stats.computeIfAbsent(endpoint, ignored -> new EndpointMutableStats());
        synchronized (target) {
            target.successCount.incrementAndGet();
            target.lastSuccessAt = Instant.now();
            target.consecutiveFailureCount = 0L;
            target.circuitOpenUntil = null;
            target.halfOpenInFlight = false;
            target.halfOpenSince = null;
        }
    }

    public void recordFailure(String endpoint, String reason) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        EndpointMutableStats target = stats.computeIfAbsent(endpoint, ignored -> new EndpointMutableStats());
        synchronized (target) {
            Instant now = Instant.now();
            target.failureCount.incrementAndGet();
            target.lastFailureAt = now;
            target.lastFailureReason = reason;
            target.halfOpenInFlight = false;
            target.halfOpenSince = null;
            target.consecutiveFailureCount += 1L;
            if (circuitBreakerEnabled && target.consecutiveFailureCount >= circuitFailureThreshold) {
                target.circuitOpenUntil = now.plusMillis(circuitOpenMs);
                target.lastCircuitOpenedAt = now;
            }
        }
    }

    public List<EndpointStats> snapshot(List<String> configuredEndpoints) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (configuredEndpoints != null) {
            configuredEndpoints.stream()
                .filter(endpoint -> endpoint != null && !endpoint.isBlank())
                .forEach(ordered::add);
        }
        stats.keySet().stream()
            .filter(endpoint -> endpoint != null && !endpoint.isBlank())
            .sorted(Comparator.naturalOrder())
            .forEach(ordered::add);

        List<EndpointStats> output = new ArrayList<>();
        Instant now = Instant.now();
        for (String endpoint : ordered) {
            EndpointMutableStats state = stats.get(endpoint);
            if (state == null) {
                output.add(new EndpointStats(
                    endpoint,
                    0,
                    0,
                    null,
                    null,
                    null,
                    0L,
                    circuitBreakerEnabled ? "CLOSED" : "DISABLED",
                    null,
                    null,
                    false
                ));
                continue;
            }
            synchronized (state) {
                cleanupStaleHalfOpen(state, now);
                String circuitState = resolveCircuitState(state, now);
                output.add(new EndpointStats(
                    endpoint,
                    state.successCount.get(),
                    state.failureCount.get(),
                    state.lastSuccessAt,
                    state.lastFailureAt,
                    state.lastFailureReason,
                    state.consecutiveFailureCount,
                    circuitState,
                    state.circuitOpenUntil,
                    state.lastCircuitOpenedAt,
                    state.halfOpenInFlight
                ));
            }
        }
        return output;
    }

    private void cleanupStaleHalfOpen(EndpointMutableStats state, Instant now) {
        if (!state.halfOpenInFlight || state.halfOpenSince == null) {
            return;
        }
        if (state.halfOpenSince.plusMillis(halfOpenTrialTimeoutMs).isBefore(now)) {
            state.halfOpenInFlight = false;
            state.halfOpenSince = null;
        }
    }

    private String resolveCircuitState(EndpointMutableStats state, Instant now) {
        if (!circuitBreakerEnabled) {
            return "DISABLED";
        }
        if (state.halfOpenInFlight) {
            return "HALF_OPEN";
        }
        if (state.circuitOpenUntil != null && state.circuitOpenUntil.isAfter(now)) {
            return "OPEN";
        }
        return "CLOSED";
    }

    public record EndpointAttemptDecision(
        boolean allowed,
        boolean halfOpen,
        String state,
        Instant reopenAt
    ) {}

    public record EndpointStats(
        String endpoint,
        long successCount,
        long failureCount,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastFailureReason,
        long consecutiveFailureCount,
        String circuitState,
        Instant circuitOpenUntil,
        Instant lastCircuitOpenedAt,
        boolean halfOpenInFlight
    ) {}

    private static final class EndpointMutableStats {
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private volatile Instant lastSuccessAt;
        private volatile Instant lastFailureAt;
        private volatile String lastFailureReason;
        private volatile long consecutiveFailureCount;
        private volatile Instant circuitOpenUntil;
        private volatile Instant lastCircuitOpenedAt;
        private volatile boolean halfOpenInFlight;
        private volatile Instant halfOpenSince;
    }
}
