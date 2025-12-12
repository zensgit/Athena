package com.ecm.core.event.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * External event message for document creation.
 * Published to message broker for external systems (e.g. DedupCAD, PLM).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCreatedMessage {
    private UUID documentId;
    private String name;
    private String mimeType;
    private String contentId;
    private Long size;
    private String createdBy;
    private LocalDateTime createdAt;
    private UUID parentId;
    private Map<String, String> metadata;

    @Builder.Default
    private String eventVersion = "1.0";
}
