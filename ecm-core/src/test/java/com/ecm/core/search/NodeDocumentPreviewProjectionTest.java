package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NodeDocumentPreviewProjectionTest {

    @Test
    @DisplayName("Search projection maps generic binary sources to unsupported preview status")
    void fromNodeProjectsGenericBinaryAsUnsupported() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("blob.bin");
        document.setPath("/blob.bin");
        document.setMimeType("application/octet-stream");

        NodeDocument projected = NodeDocument.fromNode(document);

        assertEquals("UNSUPPORTED", projected.getPreviewStatus());
        assertEquals("Preview definition is not registered for generic binary sources", projected.getPreviewFailureReason());
        assertEquals("UNSUPPORTED", projected.getPreviewFailureCategory());
    }

    @Test
    @DisplayName("Search projection normalizes unsupported preview failures to unsupported status")
    void fromNodeProjectsUnsupportedFailureAsUnsupported() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("drawing.dwg");
        document.setPath("/drawing.dwg");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("unsupported_media_type");

        NodeDocument projected = NodeDocument.fromNode(document);

        assertEquals("UNSUPPORTED", projected.getPreviewStatus());
        assertEquals("unsupported_media_type", projected.getPreviewFailureReason());
        assertEquals("UNSUPPORTED", projected.getPreviewFailureCategory());
    }

    @Test
    @DisplayName("Search projection keeps applicable unscheduled previews as pending")
    void fromNodeLeavesApplicablePreviewPendingWhenNotScheduled() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("report.pdf");
        document.setPath("/report.pdf");
        document.setMimeType("application/pdf");

        NodeDocument projected = NodeDocument.fromNode(document);

        assertNull(projected.getPreviewStatus());
        assertNull(projected.getPreviewFailureReason());
        assertNull(projected.getPreviewFailureCategory());
    }
}
