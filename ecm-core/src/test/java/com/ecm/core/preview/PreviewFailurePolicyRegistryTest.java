package com.ecm.core.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewFailurePolicyRegistryTest {

    @Test
    void resolvesPoliciesByMimeTypeAndFileName() {
        PreviewFailurePolicyRegistry registry = new PreviewFailurePolicyRegistry();

        assertEquals("cad", registry.resolve("application/dwg", "drawing.dwg").key());
        assertEquals("pdf", registry.resolve("application/pdf", "file.pdf").key());
        assertEquals("office", registry.resolve("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "doc.docx").key());
        assertEquals("image", registry.resolve("image/png", "image.png").key());
        assertEquals("text", registry.resolve("text/plain", "notes.txt").key());
        assertEquals("default", registry.resolve("application/octet-stream", "blob.bin").key());
    }

    @Test
    void updateClampsPolicyBounds() {
        PreviewFailurePolicyRegistry registry = new PreviewFailurePolicyRegistry();

        PreviewFailurePolicyRegistry.PreviewFailurePolicy updated = registry.upsert(
            "cad",
            new PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate(99, 99999999L, 99.0d, 99999999L)
        );

        assertEquals(10, updated.maxAttempts());
        assertEquals(3600000L, updated.retryDelayMs());
        assertEquals(10.0d, updated.backoffMultiplier());
        assertEquals(86400000L, updated.quietPeriodMs());
    }

    @Test
    void listPoliciesKeepsDefaultProfile() {
        PreviewFailurePolicyRegistry registry = new PreviewFailurePolicyRegistry();
        assertTrue(registry.listPolicies().stream().anyMatch(policy -> "default".equals(policy.key())));
    }
}
