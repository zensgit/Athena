package com.ecm.core.preview;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PreviewTransformTraceBuffer {

    private static final int MAX_LIST_LIMIT = 200;
    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final int MAX_STAGE_LENGTH = 64;

    private final AtomicLong requestCounter = new AtomicLong(1L);
    private final ConcurrentHashMap<String, TraceState> traces = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> requestOrder = new ConcurrentLinkedDeque<>();

    private volatile boolean enabled = true;
    private volatile int maxRequests = 100;
    private volatile int maxEventsPerTrace = 80;

    @Value("${ecm.preview.trace.enabled:true}")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Value("${ecm.preview.trace.max-requests:100}")
    public void setMaxRequests(int maxRequests) {
        this.maxRequests = Math.max(10, maxRequests);
    }

    @Value("${ecm.preview.trace.max-events-per-request:80}")
    public void setMaxEventsPerTrace(int maxEventsPerTrace) {
        this.maxEventsPerTrace = Math.max(10, maxEventsPerTrace);
    }

    public String start(UUID documentId, String mimeType, String source) {
        if (!enabled) {
            return null;
        }
        String requestId = "pv-" + requestCounter.getAndIncrement();
        TraceState state = new TraceState(
            requestId,
            documentId,
            normalizeMimeType(mimeType),
            normalizeText(source, 64),
            Instant.now()
        );
        state.appendEvent("START", "Preview request started", maxEventsPerTrace);
        traces.put(requestId, state);
        requestOrder.addFirst(requestId);
        trimOverflow();
        return requestId;
    }

    public void append(String requestId, String stage, String message) {
        if (!enabled || requestId == null || requestId.isBlank()) {
            return;
        }
        TraceState state = traces.get(requestId.trim());
        if (state == null) {
            return;
        }
        state.appendEvent(stage, message, maxEventsPerTrace);
    }

    public void complete(String requestId, String status, boolean retryNeeded, String failureReason) {
        if (!enabled || requestId == null || requestId.isBlank()) {
            return;
        }
        TraceState state = traces.get(requestId.trim());
        if (state == null) {
            return;
        }
        state.complete(status, retryNeeded, failureReason, maxEventsPerTrace);
    }

    public List<TraceSnapshot> snapshot(int limit, String requestIdFilter) {
        if (!enabled) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        String normalizedFilter = requestIdFilter == null || requestIdFilter.isBlank()
            ? null
            : requestIdFilter.trim().toLowerCase(Locale.ROOT);

        List<TraceSnapshot> result = new ArrayList<>();
        for (String requestId : requestOrder) {
            TraceState state = traces.get(requestId);
            if (state == null) {
                continue;
            }
            if (normalizedFilter != null && !requestId.toLowerCase(Locale.ROOT).contains(normalizedFilter)) {
                continue;
            }
            result.add(state.snapshot());
            if (result.size() >= safeLimit) {
                break;
            }
        }
        return result;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void trimOverflow() {
        while (requestOrder.size() > maxRequests) {
            String oldest = requestOrder.pollLast();
            if (oldest == null) {
                return;
            }
            traces.remove(oldest);
        }
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return normalizeText(normalized, 128);
    }

    private static String normalizeStage(String stage) {
        String normalized = stage == null ? "" : stage.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "EVENT";
        }
        normalized = normalized.replaceAll("\\s+", "_");
        if (normalized.length() > MAX_STAGE_LENGTH) {
            normalized = normalized.substring(0, MAX_STAGE_LENGTH);
        }
        return normalized;
    }

    private static String normalizeText(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        String normalized = input.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(1, maxLength - 3)) + "...";
    }

    private static final class TraceState {
        private final String requestId;
        private final UUID documentId;
        private final String mimeType;
        private final String source;
        private final Instant startedAt;
        private final Deque<TraceEventSnapshot> events = new ArrayDeque<>();

        private Instant finishedAt;
        private String status;
        private boolean retryNeeded;
        private String failureReason;

        private TraceState(String requestId, UUID documentId, String mimeType, String source, Instant startedAt) {
            this.requestId = requestId;
            this.documentId = documentId;
            this.mimeType = mimeType;
            this.source = source;
            this.startedAt = startedAt;
            this.status = "RUNNING";
            this.retryNeeded = false;
        }

        private synchronized void appendEvent(String stage, String message, int maxEventsPerTrace) {
            String safeStage = normalizeStage(stage);
            String safeMessage = normalizeText(message, MAX_MESSAGE_LENGTH);
            if (events.size() >= maxEventsPerTrace) {
                events.removeFirst();
            }
            events.addLast(new TraceEventSnapshot(Instant.now(), safeStage, safeMessage));
        }

        private synchronized void complete(
            String status,
            boolean retryNeeded,
            String failureReason,
            int maxEventsPerTrace
        ) {
            this.status = normalizeText(status == null ? "UNKNOWN" : status, 32);
            this.retryNeeded = retryNeeded;
            this.failureReason = normalizeText(failureReason, MAX_MESSAGE_LENGTH);
            this.finishedAt = Instant.now();
            String completionMessage = retryNeeded
                ? "Preview completed with retry hint"
                : "Preview completed";
            appendEvent("COMPLETE", completionMessage, maxEventsPerTrace);
            if (this.failureReason != null && !this.failureReason.isBlank()) {
                appendEvent("FAILURE_REASON", this.failureReason, maxEventsPerTrace);
            }
        }

        private synchronized TraceSnapshot snapshot() {
            List<TraceEventSnapshot> eventSnapshots = new ArrayList<>(events);
            String latestMessage = eventSnapshots.isEmpty()
                ? null
                : eventSnapshots.get(eventSnapshots.size() - 1).message();
            return new TraceSnapshot(
                requestId,
                documentId,
                mimeType,
                source,
                startedAt,
                finishedAt,
                status,
                retryNeeded,
                failureReason,
                latestMessage,
                eventSnapshots
            );
        }
    }

    public record TraceSnapshot(
        String requestId,
        UUID documentId,
        String mimeType,
        String source,
        Instant startedAt,
        Instant finishedAt,
        String status,
        boolean retryNeeded,
        String failureReason,
        String latestMessage,
        List<TraceEventSnapshot> events
    ) {}

    public record TraceEventSnapshot(
        Instant at,
        String stage,
        String message
    ) {}
}
