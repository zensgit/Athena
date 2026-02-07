package com.ecm.core.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SearchHighlightHelper {

    private static final List<String> SUMMARY_FIELDS = List.of(
        "description",
        "content",
        "textContent",
        "extractedText",
        "title",
        "name"
    );
    private static final Map<String, String> FIELD_LABELS = Map.of(
        "name", "Name",
        "title", "Title",
        "description", "Description",
        "content", "Content",
        "textContent", "Text",
        "extractedText", "Extracted text",
        "tags", "Tags",
        "categories", "Categories",
        "correspondent", "Correspondent"
    );
    private static final int MAX_SNIPPET_LENGTH = 280;
    private static final String EM_OPEN_TOKEN = "__ECM_EM_OPEN__";
    private static final String EM_CLOSE_TOKEN = "__ECM_EM_CLOSE__";

    private SearchHighlightHelper() {
    }

    static List<String> resolveMatchFields(Map<String, List<String>> highlights) {
        if (highlights == null || highlights.isEmpty()) {
            return List.of();
        }
        List<String> fields = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : highlights.entrySet()) {
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            String sample = sanitizeSnippet(values.get(0));
            if (sample == null || sample.isBlank()) {
                continue;
            }
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            fields.add(key);
        }
        return fields.stream().distinct().sorted().toList();
    }

    static String resolveHighlightSummary(Map<String, List<String>> highlights) {
        if (highlights == null || highlights.isEmpty()) {
            return null;
        }
        List<String> summaries = new ArrayList<>();
        for (String field : SUMMARY_FIELDS) {
            List<String> values = highlights.get(field);
            if (values != null && !values.isEmpty()) {
                String summary = formatSummary(field, values.get(0));
                if (summary == null || summary.isBlank()) {
                    continue;
                }
                summaries.add(summary);
                if (summaries.size() >= 2) {
                    break;
                }
            }
        }
        if (summaries.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : highlights.entrySet()) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    String summary = formatSummary(entry.getKey(), values.get(0));
                    if (summary != null && !summary.isBlank()) {
                        summaries.add(summary);
                        break;
                    }
                }
            }
        }
        if (summaries.isEmpty()) {
            return null;
        }
        return String.join(" • ", summaries);
    }

    private static String formatSummary(String field, String snippet) {
        String cleanSnippet = sanitizeSnippet(snippet);
        if (cleanSnippet == null || cleanSnippet.isBlank()) {
            return null;
        }
        String label = FIELD_LABELS.getOrDefault(field, toLabel(field));
        if (label == null || label.isBlank()) {
            return cleanSnippet;
        }
        return label + ": " + cleanSnippet;
    }

    private static String toLabel(String field) {
        if (field == null || field.isBlank()) {
            return field;
        }
        return field.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
    }

    private static String sanitizeSnippet(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return null;
        }
        String sanitized = snippet
            .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
            .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
            .replaceAll("(?is)<\\s*em\\s*>", EM_OPEN_TOKEN)
            .replaceAll("(?is)<\\s*/\\s*em\\s*>", EM_CLOSE_TOKEN)
            .replaceAll("(?is)<[^>]+>", "")
            .replace("&nbsp;", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (sanitized.isEmpty()) {
            return null;
        }
        if (sanitized.length() > MAX_SNIPPET_LENGTH) {
            sanitized = sanitized.substring(0, MAX_SNIPPET_LENGTH - 3) + "...";
        }
        return sanitized
            .replace(EM_OPEN_TOKEN, "<em>")
            .replace(EM_CLOSE_TOKEN, "</em>");
    }
}
