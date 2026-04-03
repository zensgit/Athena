package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeDocumentArchiveProjectionTest {

    @Test
    @DisplayName("Search projection includes archive status")
    void fromNodeProjectsArchiveStatus() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("contract.pdf");
        document.setPath("/contract.pdf");
        document.setMimeType("application/pdf");
        document.setArchiveStatus(Node.ArchiveStatus.ARCHIVED);

        NodeDocument projected = NodeDocument.fromNode(document);

        assertEquals("ARCHIVED", projected.getArchiveStatus());
    }
}
