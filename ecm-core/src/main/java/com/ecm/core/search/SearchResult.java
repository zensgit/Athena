package com.ecm.core.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Search result with highlighting support.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String id;
    private String name;
    private String description;
    private String path;
    private String nodeType;
    private String parentId;
    private String mimeType;
    private Long fileSize;
    private String createdBy;
    private LocalDateTime createdDate;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedDate;
    private float score;
    private Map<String, List<String>> highlights;
    private List<String> matchFields;
    private String highlightSummary;
    private List<String> tags;
    private List<String> categories;
    private String correspondent;
    private String previewStatus;
    private String previewFailureReason;
    private String previewFailureCategory;

    /**
     * Get human-readable file size.
     */
    public String getFileSizeFormatted() {
        if (fileSize == null) return "N/A";
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
    }
}
