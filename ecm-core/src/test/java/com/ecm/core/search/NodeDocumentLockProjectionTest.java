package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.LockLifetime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NodeDocumentLockProjectionTest {

    @Test
    @DisplayName("Search projection hides expired locks")
    void fromNodeHidesExpiredLocks() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("contract.pdf");
        document.setPath("/contract.pdf");
        document.setMimeType("application/pdf");
        document.applyLock("alice", LocalDateTime.now().minusHours(1), LockLifetime.EPHEMERAL, LocalDateTime.now().minusMinutes(2));

        NodeDocument projected = NodeDocument.fromNode(document);

        assertFalse(projected.isLocked());
    }
}
