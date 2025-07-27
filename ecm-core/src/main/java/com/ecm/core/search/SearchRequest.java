package com.ecm.core.search;

import lombok.Data;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SearchRequest {
    private String query;
    private SearchFilters filters;
    private Pageable pageable;
    private boolean highlightEnabled = true;
    private List<String> facets;
}

@Data
class SearchFilters {
    private List<String> nodeTypes;
    private List<String> mimeTypes;
    private String createdBy;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private Long minSize;
    private Long maxSize;
    private List<String> tags;
    private List<String> categories;
    private String path;
    private boolean includeDeleted = false;
}