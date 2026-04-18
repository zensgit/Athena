package com.ecm.core.dto;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.LockLifetime;
import com.ecm.core.entity.Node;
import com.ecm.core.model.Category;
import com.ecm.core.model.Tag;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.preview.PreviewStatusSemantics;

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
    String typeQName,
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
    LocalDateTime lockedDate,
    LockLifetime lockLifetime,
    LocalDateTime lockExpiresAt,
    boolean checkedOut,
    String checkoutUser,
    LocalDateTime checkoutDate,
    UUID workingCopyOf,
    boolean isWorkingCopy,
    String createdBy,
    LocalDateTime createdDate,
    String lastModifiedBy,
    LocalDateTime lastModifiedDate,
    String previewStatus,
    String previewFailureReason,
    String previewFailureCategory,
    LocalDateTime previewLastUpdated
) {
    public static NodeDto from(Node node) {
        return from(node, null);
    }

    public static NodeDto from(Node node, Map<String, Object> propertiesOverride) {
        if (node == null) {
            return null;
        }

        UUID parentId = node.getParent() != null ? node.getParent().getId() : null;
        Map<String, Object> properties = propertiesOverride != null
            ? new HashMap<>(propertiesOverride)
            : (node.getProperties() != null ? new HashMap<>(node.getProperties()) : new HashMap<>());
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
        boolean checkedOut = false;
        String checkoutUser = null;
        LocalDateTime checkoutDate = null;
        UUID workingCopyOf = null;
        boolean isWorkingCopy = false;
        String previewStatus = null;
        String previewFailureReason = null;
        String previewFailureCategory = null;
        LocalDateTime previewLastUpdated = null;
        List<String> aspects = new ArrayList<>();
        if (node.getAspects() != null) {
            aspects.addAll(node.getAspects());
        }
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
            checkedOut = document.isCheckedOut();
            checkoutUser = document.getCheckoutUser();
            checkoutDate = document.getCheckoutDate();
            workingCopyOf = document.getWorkingCopyOf();
            isWorkingCopy = document.isWorkingCopy();
            previewStatus = PreviewStatusSemantics.resolveEffectiveStatus(document);
            previewFailureReason = PreviewStatusSemantics.resolveEffectiveFailureReason(document);
            previewFailureCategory = PreviewFailureClassifier.classify(previewStatus, contentType, previewFailureReason);
            previewLastUpdated = document.getPreviewLastUpdated();
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
            node.getTypeQName(),
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
            node.isEffectivelyLocked(LocalDateTime.now()),
            node.getLockedBy(),
            node.getLockedDate(),
            node.getLockLifetime(),
            node.getLockExpiresAt(),
            checkedOut,
            checkoutUser,
            checkoutDate,
            workingCopyOf,
            isWorkingCopy,
            node.getCreatedBy(),
            node.getCreatedDate(),
            node.getLastModifiedBy(),
            node.getLastModifiedDate(),
            previewStatus,
            previewFailureReason,
            previewFailureCategory,
            previewLastUpdated
        );
    }

    public NodeDto withPreviewSemantics(
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated
    ) {
        return new NodeDto(
            id,
            name,
            description,
            path,
            nodeType,
            typeQName,
            parentId,
            size,
            contentType,
            currentVersionLabel,
            correspondentId,
            correspondentName,
            properties,
            metadata,
            aspects,
            tags,
            categories,
            inheritPermissions,
            locked,
            lockedBy,
            lockedDate,
            lockLifetime,
            lockExpiresAt,
            checkedOut,
            checkoutUser,
            checkoutDate,
            workingCopyOf,
            isWorkingCopy,
            createdBy,
            createdDate,
            lastModifiedBy,
            lastModifiedDate,
            previewStatus,
            previewFailureReason,
            previewFailureCategory,
            previewLastUpdated
        );
    }
}
