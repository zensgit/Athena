package com.ecm.core.search;

import com.ecm.core.entity.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeDocumentCheckoutProjectionTest {

    @Test
    @DisplayName("Search projection includes checkout state and owner")
    void fromNodeProjectsCheckoutMetadata() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("contract.docx");
        document.setPath("/contract.docx");
        document.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        document.checkout("alice");

        NodeDocument projected = NodeDocument.fromNode(document);

        assertTrue(projected.isCheckedOut());
        assertEquals("alice", projected.getCheckoutUser());
    }

    @Test
    @DisplayName("Search projection keeps checkout metadata empty for available documents")
    void fromNodeLeavesCheckoutMetadataEmptyWhenAvailable() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("contract.docx");
        document.setPath("/contract.docx");
        document.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        NodeDocument projected = NodeDocument.fromNode(document);

        assertFalse(projected.isCheckedOut());
        assertEquals(null, projected.getCheckoutUser());
    }
}
