package com.ecm.core.dto;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.model.Category;
import com.ecm.core.model.Tag;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NodeDto(
    UUID id,
    String name,
    String description,
    String path,
    String nodeType,
    UUID parentId,
    Long size,
    String contentType,
    String currentVersionLabel,
    UUID correspondentId,
    String correspondentName,
    Map<String, Object> properties,
    Map<String, Object> metadata,
    List<String> aspects,
    List<String> tags,
    List<String> categories,
    boolean inheritPermissions,
    boolean locked,
    String lockedBy,
    String createdBy,
    LocalDateTime createdDate,
    String lastModifiedBy,
    LocalDateTime lastModifiedDate
) {
    public static NodeDto from(Node node) {
        if (node == null) {
            return null;
        }

        UUID parentId = node.getParent() != null ? node.getParent().getId() : null;
        Map<String, Object> properties = node.getProperties() != null ? new HashMap<>(node.getProperties()) : new HashMap<>();
        Map<String, Object> metadata = node.getMetadata() != null ? new HashMap<>(node.getMetadata()) : new HashMap<>();

        List<String> tags = new ArrayList<>();
        if (node.getTags() != null) {
            for (Tag tag : node.getTags()) {
                if (tag != null && tag.getName() != null) {
                    tags.add(tag.getName());
                }
            }
        }

        List<String> categories = new ArrayList<>();
        if (node.getCategories() != null) {
            for (Category category : node.getCategories()) {
                if (category != null && category.getName() != null) {
                    categories.add(category.getName());
                }
            }
        }

        String contentType = null;
        String currentVersionLabel = null;
        List<String> aspects = new ArrayList<>();
        if (node instanceof Document document) {
            contentType = document.getMimeType();
            if (contentType == null) {
                Object fallbackType = properties.get("mimeType");
                if (fallbackType == null) {
                    fallbackType = properties.get("contentType");
                }
                if (fallbackType instanceof String fallbackString && !fallbackString.isBlank()) {
                    contentType = fallbackString;
                }
            }
            currentVersionLabel = document.getVersionLabel() != null ? document.getVersionLabel() : document.getVersionString();
            if (document.isVersioned()) {
                aspects.add("cm:versionable");
            }
        }

        UUID correspondentId = null;
        String correspondentName = null;
        if (node.getCorrespondent() != null) {
            correspondentId = node.getCorrespondent().getId();
            correspondentName = node.getCorrespondent().getName();
        }

        return new NodeDto(
            node.getId(),
            node.getName(),
            node.getDescription(),
            node.getPath(),
            node.getNodeType().name(),
            parentId,
            node.getSize(),
            contentType,
            currentVersionLabel,
            correspondentId,
            correspondentName,
            properties,
            metadata,
            aspects,
            tags,
            categories,
            node.isInheritPermissions(),
            node.isLocked(),
            node.getLockedBy(),
            node.getCreatedBy(),
            node.getCreatedDate(),
            node.getLastModifiedBy(),
            node.getLastModifiedDate()
        );
    }
}
