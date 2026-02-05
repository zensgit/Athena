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
        for (String field : SUMMARY_FIELDS) {
            List<String> values = highlights.get(field);
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
        }
        for (List<String> values : highlights.values()) {
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
        }
        return null;
    }
}
