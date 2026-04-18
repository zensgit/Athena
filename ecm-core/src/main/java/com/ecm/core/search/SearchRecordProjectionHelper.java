package com.ecm.core.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.util.List;
import java.util.Map;

final class SearchRecordProjectionHelper {

    static final List<String> RECORD_PROJECTION_PROPERTIES = List.of(
        "rm:declaredAt",
        "rm:declaredBy",
        "rm:declaredVersionLabel",
        "rm:declarationComment",
        "rm:recordCategoryId",
        "rm:recordCategoryName",
        "rm:recordCategoryPath"
    );

    private SearchRecordProjectionHelper() {
    }

    static boolean isRecordProjection(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return false;
        }
        return RECORD_PROJECTION_PROPERTIES.stream().anyMatch(properties::containsKey);
    }

    static void applyRecordOnlyFilter(BoolQuery.Builder bool, Boolean recordOnly) {
        if (recordOnly == null) {
            return;
        }
        Query recordProjectionQuery = buildRecordProjectionQuery();
        if (Boolean.TRUE.equals(recordOnly)) {
            bool.filter(recordProjectionQuery);
            return;
        }
        bool.mustNot(recordProjectionQuery);
    }

    static Query buildRecordProjectionQuery() {
        return Query.of(q -> q.bool(b -> {
            for (String property : RECORD_PROJECTION_PROPERTIES) {
                b.should(s -> s.exists(e -> e.field("properties." + property)));
            }
            b.minimumShouldMatch("1");
            return b;
        }));
    }
}
