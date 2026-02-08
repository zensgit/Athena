package com.ecm.core.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PreviewFailureClassifierTest {

    @Test
    void returnsNullWhenStatusIsNotFailed() {
        assertNull(PreviewFailureClassifier.classify("READY", "application/pdf", null));
    }

    @Test
    void classifiesUnsupportedByMimeType() {
        assertEquals(
            PreviewFailureClassifier.CATEGORY_UNSUPPORTED,
            PreviewFailureClassifier.classify("FAILED", "application/octet-stream", "anything")
        );
    }

    @Test
    void classifiesUnsupportedByReason() {
        assertEquals(
            PreviewFailureClassifier.CATEGORY_UNSUPPORTED,
            PreviewFailureClassifier.classify("FAILED", "application/pdf", "Preview not supported for mime type: application/pdf")
        );
    }

    @Test
    void classifiesTemporaryFailure() {
        assertEquals(
            PreviewFailureClassifier.CATEGORY_TEMPORARY,
            PreviewFailureClassifier.classify("FAILED", "application/pdf", "Error generating preview: timeout contacting renderer")
        );
    }

    @Test
    void classifiesPermanentFailureByDefault() {
        assertEquals(
            PreviewFailureClassifier.CATEGORY_PERMANENT,
            PreviewFailureClassifier.classify("FAILED", "application/pdf", "Corrupt document structure")
        );
    }

    @Test
    void treatsUnsupportedStatusAsUnsupportedCategory() {
        assertEquals(
            PreviewFailureClassifier.CATEGORY_UNSUPPORTED,
            PreviewFailureClassifier.classify("UNSUPPORTED", "application/pdf", null)
        );
    }
}
