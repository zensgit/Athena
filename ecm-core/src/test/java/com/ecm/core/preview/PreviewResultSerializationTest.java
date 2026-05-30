package com.ecm.core.preview;

import org.junit.jupiter.api.Test;
import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.core.serializer.DefaultSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PreviewResultSerializationTest {

    @Test
    void previewResultCanBeSerializedBySpringDefaultSerializer() throws Exception {
        UUID documentId = UUID.randomUUID();
        PreviewPage page = new PreviewPage();
        page.setPageNumber(1);
        page.setWidth(800);
        page.setHeight(1000);
        page.setFormat("png");
        page.setContent(new byte[] {1, 2, 3});
        page.setTextContent("preview text");
        page.setUrl("/api/v1/documents/" + documentId + "/preview/pages/1");

        PreviewResult result = new PreviewResult();
        result.setDocumentId(documentId);
        result.setTraceRequestId("trace-1");
        result.setMimeType("application/pdf");
        result.setSupported(true);
        result.setStatus("READY");
        result.setMessage("Generated preview");
        result.setRetryNeeded(false);
        result.setRetryHint(null);
        result.setFailureReason(null);
        result.setFailureCategory(null);
        result.setPages(List.of(page));
        result.setPageCount(1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new DefaultSerializer().serialize(result, out);

        Object deserialized = new DefaultDeserializer().deserialize(new ByteArrayInputStream(out.toByteArray()));
        PreviewResult restored = assertInstanceOf(PreviewResult.class, deserialized);
        assertEquals(documentId, restored.getDocumentId());
        assertEquals("application/pdf", restored.getMimeType());
        assertEquals("READY", restored.getStatus());
        assertEquals(1, restored.getPageCount());
        assertEquals(1, restored.getPages().size());
        PreviewPage restoredPage = restored.getPages().get(0);
        assertEquals(1, restoredPage.getPageNumber());
        assertEquals("png", restoredPage.getFormat());
        assertArrayEquals(new byte[] {1, 2, 3}, restoredPage.getContent());
    }
}
