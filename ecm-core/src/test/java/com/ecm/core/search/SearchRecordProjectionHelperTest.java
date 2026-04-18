package com.ecm.core.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchRecordProjectionHelperTest {

    @Test
    void shouldRecognizeRecordProjectionWhenRmFieldsExist() {
        assertTrue(SearchRecordProjectionHelper.isRecordProjection(Map.of(
            "rm:declaredAt", "2026-04-17T10:00:00"
        )));
        assertFalse(SearchRecordProjectionHelper.isRecordProjection(Map.of("cm:title", "Contract")));
        assertFalse(SearchRecordProjectionHelper.isRecordProjection(null));
    }

    @Test
    void shouldBuildExistsQueryAcrossAllRmProjectionFields() {
        Query query = SearchRecordProjectionHelper.buildRecordProjectionQuery();

        assertTrue(query.isBool());
        assertEquals("1", query.bool().minimumShouldMatch());
        assertEquals(
            SearchRecordProjectionHelper.RECORD_PROJECTION_PROPERTIES.stream()
                .map(field -> "properties." + field)
                .collect(Collectors.toSet()),
            query.bool().should().stream()
                .filter(Query::isExists)
                .map(item -> item.exists().field())
                .collect(Collectors.toSet())
        );
    }
}
