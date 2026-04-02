package com.ecm.core.preview;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewDeadLetterRegistryTest {

    @Test
    void recordsAndUpdatesDeadLetterEntry() {
        PreviewDeadLetterRegistry registry = new PreviewDeadLetterRegistry();
        registry.setEnabled(true);
        registry.setMaxEntries(5000);

        UUID documentId = UUID.randomUUID();
        registry.record(documentId, "timeout", "TEMPORARY", "cad", "QUEUE_RETRY_EXHAUSTED", 3);
        registry.record(documentId, "timeout again", "TEMPORARY", "cad", "QUEUE_RETRY_EXHAUSTED", 4);

        List<PreviewDeadLetterRegistry.DeadLetterEntry> items = registry.list(20);
        assertEquals(1, items.size());
        PreviewDeadLetterRegistry.DeadLetterEntry entry = items.get(0);
        assertEquals(PreviewDeadLetterRegistry.buildEntryKey(documentId, "preview"), entry.entryKey());
        assertEquals(documentId, entry.documentId());
        assertEquals("preview", entry.renditionKey());
        assertEquals(4, entry.attempts());
        assertEquals(2L, entry.occurrences());
        assertEquals(0L, entry.replayCount());
        assertEquals(null, entry.lastReplayAt());
        assertEquals("cad", entry.policyKey());
        assertEquals("QUEUE_RETRY_EXHAUSTED", entry.sourceStage());
        assertTrue(entry.reason().contains("timeout"));
    }

    @Test
    void trimsOldestEntriesWhenCapacityExceeded() {
        PreviewDeadLetterRegistry registry = new PreviewDeadLetterRegistry();
        registry.setEnabled(true);
        registry.setMaxEntries(100);

        UUID first = UUID.randomUUID();
        registry.record(first, "first", "TEMPORARY", "default", "QUEUE_TERMINAL", 1);
        for (int i = 0; i < 105; i++) {
            registry.record(UUID.randomUUID(), "r-" + i, "TEMPORARY", "default", "QUEUE_TERMINAL", 1);
        }

        List<PreviewDeadLetterRegistry.DeadLetterEntry> items = registry.list(200);
        assertEquals(100, items.size());
        assertTrue(items.stream().noneMatch(item -> item.documentId().equals(first)));
    }

    @Test
    void removeDeletesEntry() {
        PreviewDeadLetterRegistry registry = new PreviewDeadLetterRegistry();
        registry.setEnabled(true);
        registry.setMaxEntries(5000);

        UUID documentId = UUID.randomUUID();
        registry.record(documentId, "preview", "timeout", "TEMPORARY", "cad", "QUEUE_RETRY_EXHAUSTED", 3);
        assertEquals(1, registry.getItemCount());

        registry.remove(documentId, "preview");
        assertEquals(0, registry.getItemCount());
        assertEquals(0, registry.list(10).size());
    }

    @Test
    void marksReplayAttemptWithTimestampAndCounter() {
        PreviewDeadLetterRegistry registry = new PreviewDeadLetterRegistry();
        registry.setEnabled(true);
        registry.setMaxEntries(5000);

        UUID documentId = UUID.randomUUID();
        registry.record(documentId, "timeout", "TEMPORARY", "cad", "QUEUE_RETRY_EXHAUSTED", 3);
        registry.markReplayAttempt(documentId, java.time.Instant.parse("2026-03-06T14:30:00Z"));
        registry.markReplayAttempt(documentId, java.time.Instant.parse("2026-03-06T14:40:00Z"));

        List<PreviewDeadLetterRegistry.DeadLetterEntry> items = registry.list(20);
        assertEquals(1, items.size());
        PreviewDeadLetterRegistry.DeadLetterEntry entry = items.get(0);
        assertEquals(2L, entry.replayCount());
        assertEquals("2026-03-06T14:40:00Z", String.valueOf(entry.lastReplayAt()));
    }

    @Test
    void storesDeadLetterEntriesByDocumentAndRendition() {
        PreviewDeadLetterRegistry registry = new PreviewDeadLetterRegistry();
        registry.setEnabled(true);
        registry.setMaxEntries(5000);

        UUID documentId = UUID.randomUUID();
        registry.record(documentId, "preview", "preview timeout", "TEMPORARY", "default", "QUEUE_RETRY_EXHAUSTED", 2);
        registry.record(documentId, "thumbnail", "thumb timeout", "TEMPORARY", "default", "QUEUE_RETRY_EXHAUSTED", 2);

        List<PreviewDeadLetterRegistry.DeadLetterEntry> items = registry.list(20);
        assertEquals(2, items.size());
        assertTrue(items.stream().anyMatch(item -> "preview".equals(item.renditionKey())));
        assertTrue(items.stream().anyMatch(item -> "thumbnail".equals(item.renditionKey())));

        registry.remove(documentId, "thumbnail");
        List<PreviewDeadLetterRegistry.DeadLetterEntry> afterRemove = registry.list(20);
        assertEquals(1, afterRemove.size());
        assertEquals("preview", afterRemove.get(0).renditionKey());
    }
}
