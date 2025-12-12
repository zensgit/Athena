package com.ecm.core.search;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Search filters for advanced search operations.
 */
@Data
public class SearchFilters {
    private List<String> nodeTypes;
    private List<String> mimeTypes;
    private String createdBy;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private LocalDateTime modifiedFrom;
    private LocalDateTime modifiedTo;
    private Long minSize;
    private Long maxSize;
    private List<String> tags;
    private List<String> categories;
    private String path;
    private boolean includeDeleted = false;
}
