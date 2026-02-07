package com.ecm.core.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchHighlightHelperTest {

    @Test
    @DisplayName("Highlight summary keeps em tags and strips unsafe html tags")
    void resolveHighlightSummarySanitizesSnippet() {
        Map<String, List<String>> highlights = Map.of(
            "content",
            List.of("Hello <em>world</em> <img src=x onerror=1 /><script>alert('x')</script>")
        );

        String summary = SearchHighlightHelper.resolveHighlightSummary(highlights);

        assertNotNull(summary);
        assertTrue(summary.contains("Content: Hello <em>world</em>"));
        assertFalse(summary.contains("<img"));
        assertFalse(summary.contains("<script"));
        assertFalse(summary.contains("alert("));
    }

    @Test
    @DisplayName("Match fields ignore snippets that collapse to empty content")
    void resolveMatchFieldsSkipsEmptyAfterSanitize() {
        Map<String, List<String>> highlights = Map.of(
            "content", List.of("<script>alert('x')</script>"),
            "title", List.of("Hello <em>Title</em>")
        );

        List<String> fields = SearchHighlightHelper.resolveMatchFields(highlights);

        assertEquals(List.of("title"), fields);
    }

    @Test
    @DisplayName("Highlight summary truncates very long snippets")
    void resolveHighlightSummaryTruncatesLongSnippet() {
        String longSnippet = "A".repeat(400);
        Map<String, List<String>> highlights = Map.of("content", List.of(longSnippet));

        String summary = SearchHighlightHelper.resolveHighlightSummary(highlights);

        assertNotNull(summary);
        assertTrue(summary.length() < 320);
        assertTrue(summary.endsWith("..."));
    }
}
