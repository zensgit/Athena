package com.ecm.core.search;

import lombok.Data;

import java.util.List;

@Data
public class SearchRequest {
    private String query;
    private SearchFilters filters;
    /**
     * Simple page request to avoid direct Pageable deserialization issues.
     */
    private SimplePageRequest pageable;
    private boolean highlightEnabled = true;
    private List<String> facets;
}
