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
    private List<String> createdByList;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private LocalDateTime modifiedFrom;
    private LocalDateTime modifiedTo;
    private Long minSize;
    private Long maxSize;
    private List<String> tags;
    private List<String> categories;
    private List<String> correspondents;
    private String path;
    /**
     * Optional folder scope for "search within folder" use-cases.
     *
     * When {@code folderId} is provided, the backend resolves scope using either:
     * - {@code includeChildren=true}: path prefix of the folder (recursive)
     * - {@code includeChildren=false}: direct children via {@code parentId} term filter
     */
    private String folderId;
    private boolean includeChildren = true;
    private boolean includeDeleted = false;
    /**
     * Optional preview status filter(s).
     *
     * The UI treats a missing preview status as {@code PENDING}. When the client sends {@code PENDING},
     * the backend interprets it as "previewStatus is missing" in the search index.
     */
    private List<String> previewStatuses;
}
