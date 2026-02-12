package com.ecm.core.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviewStatusFilterHelperTest {

    @Test
    void normalizeMapsAliasesAndIgnoresUnknownStatuses() {
        List<String> normalized = PreviewStatusFilterHelper.normalize(
            List.of("waiting", "in_progress", "error", "UNKNOWN", "ready", "waiting")
        );

        assertEquals(List.of("QUEUED", "PROCESSING", "FAILED", "READY"), normalized);
    }

    @Test
    void normalizeMapsUnsupportedCategoryVariants() {
        List<String> normalized = PreviewStatusFilterHelper.normalize(
            List.of("unsupported_media_type", "unsupported_mime", "preview_unsupported", "unsupported")
        );

        assertEquals(List.of("UNSUPPORTED"), normalized);
    }
}
