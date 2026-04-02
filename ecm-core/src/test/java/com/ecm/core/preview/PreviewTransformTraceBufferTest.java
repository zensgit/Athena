package com.ecm.core.preview;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewTransformTraceBufferTest {

    @Test
    void traceLifecycleIsCaptured() {
        PreviewTransformTraceBuffer buffer = new PreviewTransformTraceBuffer();
        buffer.setEnabled(true);
        buffer.setMaxRequests(100);
        buffer.setMaxEventsPerTrace(20);

        String requestId = buffer.start(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "application/pdf; charset=utf-8",
            "preview"
        );
        buffer.append(requestId, "route", "pdf");
        buffer.append(requestId, "queue_attempt", "attempt=1");
        buffer.complete(requestId, "READY", false, null);

        List<PreviewTransformTraceBuffer.TraceSnapshot> snapshots = buffer.snapshot(10, requestId);
        assertEquals(1, snapshots.size());

        PreviewTransformTraceBuffer.TraceSnapshot snapshot = snapshots.get(0);
        assertEquals(requestId, snapshot.requestId());
        assertEquals("application/pdf", snapshot.mimeType());
        assertEquals("preview", snapshot.source());
        assertEquals("READY", snapshot.status());
        assertFalse(snapshot.retryNeeded());
        assertNull(snapshot.failureReason());
        assertEquals("Preview completed", snapshot.latestMessage());
        assertNotNull(snapshot.startedAt());
        assertNotNull(snapshot.finishedAt());
        assertTrue(snapshot.events().stream().anyMatch(event -> "ROUTE".equals(event.stage())));
        assertTrue(snapshot.events().stream().anyMatch(event -> "QUEUE_ATTEMPT".equals(event.stage())));
        assertTrue(snapshot.events().stream().anyMatch(event -> "COMPLETE".equals(event.stage())));
    }

    @Test
    void oldestTraceIsEvictedWhenCapacityExceeded() {
        PreviewTransformTraceBuffer buffer = new PreviewTransformTraceBuffer();
        buffer.setEnabled(true);
        buffer.setMaxRequests(10);
        buffer.setMaxEventsPerTrace(20);

        String first = buffer.start(UUID.randomUUID(), "application/pdf", "preview");
        for (int i = 0; i < 9; i++) {
            buffer.start(UUID.randomUUID(), "application/pdf", "preview");
        }
        String second = buffer.start(UUID.randomUUID(), "application/pdf", "preview");
        String third = buffer.start(UUID.randomUUID(), "application/pdf", "preview");

        List<PreviewTransformTraceBuffer.TraceSnapshot> snapshots = buffer.snapshot(10, null);
        assertEquals(10, snapshots.size());
        assertEquals(third, snapshots.get(0).requestId());
        assertEquals(second, snapshots.get(1).requestId());
        assertTrue(snapshots.stream().noneMatch(item -> item.requestId().equals(first)));
    }

    @Test
    void traceFilterSupportsPartialRequestId() {
        PreviewTransformTraceBuffer buffer = new PreviewTransformTraceBuffer();
        buffer.setEnabled(true);
        buffer.setMaxRequests(100);
        buffer.setMaxEventsPerTrace(20);

        String first = buffer.start(UUID.randomUUID(), "application/pdf", "preview");
        String second = buffer.start(UUID.randomUUID(), "application/pdf", "preview");

        String partial = second.substring(second.length() - 1);
        List<PreviewTransformTraceBuffer.TraceSnapshot> filtered = buffer.snapshot(10, partial);

        assertTrue(filtered.stream().anyMatch(item -> item.requestId().equals(second)));
        assertTrue(filtered.stream().noneMatch(item -> item.requestId().equals(first)));
    }
}
