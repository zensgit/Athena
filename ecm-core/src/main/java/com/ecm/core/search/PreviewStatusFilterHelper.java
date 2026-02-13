package com.ecm.core.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared Elasticsearch query builder helpers for preview status filtering.
 *
 * The UI exposes a "Preview Status" filter with an additional synthetic state {@code PENDING}
 * (meaning the preview status is missing in the search index). We implement that semantics here
 * so Search Results and Advanced Search behave consistently across pages.
 */
final class PreviewStatusFilterHelper {
    private static final Set<String> KNOWN_PREVIEW_STATUSES = Set.of(
        "READY",
        "PROCESSING",
        "QUEUED",
        "FAILED",
        "UNSUPPORTED",
        "PENDING"
    );
    private static final List<String> PREVIEW_STATUS_FACET_BUCKETS = List.of(
        "READY",
        "PROCESSING",
        "QUEUED",
        "FAILED",
        "UNSUPPORTED",
        "PENDING"
    );
    private static final Map<String, String> PREVIEW_STATUS_ALIAS_MAP = Map.of(
        "IN_PROGRESS", "PROCESSING",
        "RUNNING", "PROCESSING",
        "WAITING", "QUEUED",
        "ERROR", "FAILED",
        "UNSUPPORTED_MEDIA_TYPE", "UNSUPPORTED",
        "UNSUPPORTED_MIME", "UNSUPPORTED",
        "PREVIEW_UNSUPPORTED", "UNSUPPORTED"
    );
    private static final Set<String> UNSUPPORTED_MIME_TYPES = Set.of(
        "application/octet-stream",
        "binary/octet-stream",
        "application/x-empty"
    );

    // Keep aligned with PreviewFailureClassifier.isUnsupportedReason(...)
    private static final List<String> UNSUPPORTED_REASON_PHRASES = List.of(
        "preview not supported",
        "not supported for mime type",
        "not available for empty pdf content",
        "cad preview disabled",
        "cad preview service not configured"
    );

    private PreviewStatusFilterHelper() {}

    static List<String> facetBucketKeys() {
        return PREVIEW_STATUS_FACET_BUCKETS;
    }

    static Query buildFacetBucketQuery(String status) {
        if (status == null) {
            return new Query.Builder().matchNone(mn -> mn).build();
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return new Query.Builder().matchNone(mn -> mn).build();
        }
        if (PREVIEW_STATUS_ALIAS_MAP.containsKey(normalized)) {
            normalized = PREVIEW_STATUS_ALIAS_MAP.get(normalized);
        } else if (normalized.contains("UNSUPPORTED")) {
            normalized = "UNSUPPORTED";
        }

        if (!KNOWN_PREVIEW_STATUSES.contains(normalized)) {
            return new Query.Builder().matchNone(mn -> mn).build();
        }

        final String resolved = normalized;
        return new Query.Builder()
            .bool(b -> {
                // Preview status buckets only apply to documents (mirror UI behavior).
                b.filter(f -> f.term(t -> t.field("nodeType").value("DOCUMENT")));

                switch (resolved) {
                    case "PENDING" -> b.mustNot(mn -> mn.exists(e -> e.field("previewStatus")));
                    case "UNSUPPORTED" -> {
                        b.should(s -> s.term(t -> t.field("previewStatus").value("UNSUPPORTED")));
                        b.should(s -> s.bool(failed -> {
                            failed.filter(f -> f.term(t -> t.field("previewStatus").value("FAILED")));
                            failed.filter(PreviewStatusFilterHelper::buildUnsupportedSignals);
                            return failed;
                        }));
                        b.minimumShouldMatch("1");
                    }
                    case "FAILED" -> {
                        b.filter(f -> f.term(t -> t.field("previewStatus").value("FAILED")));
                        // Exclude stale/legacy unsupported failures from FAILED so facets match UI effective status.
                        b.mustNot(PreviewStatusFilterHelper::buildUnsupportedSignals);
                    }
                    default -> b.filter(f -> f.term(t -> t.field("previewStatus").value(resolved)));
                }

                return b;
            })
            .build();
    }

    static void apply(BoolQuery.Builder bool, List<String> previewStatuses) {
        List<String> normalized = normalize(previewStatuses);
        if (normalized.isEmpty()) {
            return;
        }

        // Preview status only applies to documents.
        bool.filter(f -> f.term(t -> t.field("nodeType").value("DOCUMENT")));

        bool.filter(f -> f.bool(statusBool -> {
            for (String status : normalized) {
                statusBool.should(s -> buildStatusPredicate(s, status));
            }
            statusBool.minimumShouldMatch("1");
            return statusBool;
        }));
    }

    private static co.elastic.clients.util.ObjectBuilder<co.elastic.clients.elasticsearch._types.query_dsl.Query> buildStatusPredicate(
        co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder query,
        String status
    ) {
        return switch (status) {
            case "PENDING" -> query.bool(b -> {
                b.mustNot(mn -> mn.exists(e -> e.field("previewStatus")));
                return b;
            });
            case "UNSUPPORTED" -> query.bool(b -> {
                b.should(s -> s.term(t -> t.field("previewStatus").value("UNSUPPORTED")));
                b.should(s -> s.bool(failed -> {
                    failed.filter(f -> f.term(t -> t.field("previewStatus").value("FAILED")));
                    failed.filter(PreviewStatusFilterHelper::buildUnsupportedSignals);
                    return failed;
                }));
                b.minimumShouldMatch("1");
                return b;
            });
            case "FAILED" -> query.bool(b -> {
                b.filter(f -> f.term(t -> t.field("previewStatus").value("FAILED")));
                // Exclude stale/legacy unsupported failures from FAILED so the filter matches UI effective status.
                b.mustNot(PreviewStatusFilterHelper::buildUnsupportedSignals);
                return b;
            });
            default -> query.term(t -> t.field("previewStatus").value(status));
        };
    }

    private static co.elastic.clients.util.ObjectBuilder<co.elastic.clients.elasticsearch._types.query_dsl.Query> buildUnsupportedSignals(
        co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder query
    ) {
        return query.bool(signal -> {
            for (String mimeType : UNSUPPORTED_MIME_TYPES) {
                signal.should(s -> s.term(t -> t.field("mimeType").value(mimeType)));
            }
            for (String phrase : UNSUPPORTED_REASON_PHRASES) {
                signal.should(s -> s.match(m -> m.field("previewFailureReason").query(phrase)));
            }
            signal.minimumShouldMatch("1");
            return signal;
        });
    }

    static List<String> normalize(List<String> previewStatuses) {
        if (previewStatuses == null || previewStatuses.isEmpty()) {
            return List.of();
        }
        Set<String> deduped = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String raw : previewStatuses) {
            if (raw == null) {
                continue;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }
            if (PREVIEW_STATUS_ALIAS_MAP.containsKey(normalized)) {
                normalized = PREVIEW_STATUS_ALIAS_MAP.get(normalized);
            } else if (normalized.contains("UNSUPPORTED")) {
                normalized = "UNSUPPORTED";
            } else if (!KNOWN_PREVIEW_STATUSES.contains(normalized)) {
                continue;
            }
            if (deduped.add(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }
}
