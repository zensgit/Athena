package com.ecm.core.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PreviewStatusFilterHelperTest {

    @Test
    @DisplayName("Missing preview status with generic binary mime resolves to unsupported")
    void resolveEffectiveStatusTreatsGenericBinaryAsUnsupported() {
        assertEquals(
            "UNSUPPORTED",
            PreviewStatusFilterHelper.resolveEffectiveStatus(null, "application/octet-stream", null)
        );
        assertEquals(
            "Preview definition is not registered for generic binary sources",
            PreviewStatusFilterHelper.resolveEffectiveFailureReason(null, "application/octet-stream", null)
        );
    }

    @Test
    @DisplayName("Missing preview status on applicable mime remains pending-compatible")
    void resolveEffectiveStatusKeepsApplicableMissingStatusUnset() {
        assertNull(PreviewStatusFilterHelper.resolveEffectiveStatus(null, "application/pdf", null));
        assertNull(PreviewStatusFilterHelper.resolveEffectiveFailureReason(null, "application/pdf", null));
    }

    @Test
    @DisplayName("Failed unsupported reasons normalize to unsupported")
    void resolveEffectiveStatusNormalizesUnsupportedFailures() {
        assertEquals(
            "UNSUPPORTED",
            PreviewStatusFilterHelper.resolveEffectiveStatus("FAILED", "application/pdf", "unsupported_media_type")
        );
        assertEquals(
            "unsupported_media_type",
            PreviewStatusFilterHelper.resolveEffectiveFailureReason("FAILED", "application/pdf", "unsupported_media_type")
        );
    }

    @Test
    @DisplayName("Retryable failures remain failed")
    void resolveEffectiveStatusKeepsRetryableFailureAsFailed() {
        assertEquals(
            "FAILED",
            PreviewStatusFilterHelper.resolveEffectiveStatus("FAILED", "application/pdf", "preview timeout")
        );
        assertEquals(
            "preview timeout",
            PreviewStatusFilterHelper.resolveEffectiveFailureReason("FAILED", "application/pdf", "preview timeout")
        );
    }

    @Test
    @DisplayName("Explicit preview failure category is preserved")
    void resolveEffectiveFailureCategoryPrefersExplicitCategory() {
        assertEquals(
            "UNSUPPORTED",
            PreviewStatusFilterHelper.resolveEffectiveFailureCategory(
                "FAILED",
                "application/pdf",
                "preview generation failed",
                "UNSUPPORTED"
            )
        );
    }
}
