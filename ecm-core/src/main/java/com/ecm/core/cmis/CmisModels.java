package com.ecm.core.cmis;

import java.util.List;
import java.util.Map;

public final class CmisModels {

    private CmisModels() {
    }

    public record RepositoryInfo(
        String repositoryId,
        String repositoryName,
        String vendorName,
        String productName,
        String productVersion,
        String cmisVersionSupported,
        String rootFolderId,
        List<String> capabilities
    ) {
    }

    public record TypeDefinition(
        String id,
        String displayName,
        String baseTypeId,
        boolean creatable,
        boolean fileable,
        boolean queryable,
        List<String> propertyIds
    ) {
    }

    public record ObjectEntry(
        String repositoryId,
        String objectId,
        String name,
        String baseTypeId,
        String objectTypeId,
        String path,
        String parentId,
        boolean root,
        Map<String, Object> properties,
        List<String> allowableActions
    ) {
    }

    public record ChildrenResponse(
        String repositoryId,
        String parentObjectId,
        List<ObjectEntry> objects,
        int skipCount,
        int maxItems,
        int totalNumItems,
        boolean hasMoreItems
    ) {
    }

    public record TypeChildrenResponse(
        List<TypeDefinition> types,
        int totalNumItems,
        boolean hasMoreItems
    ) {
    }

    public record QueryResponse(
        String repositoryId,
        String statement,
        List<ObjectEntry> objects,
        int skipCount,
        int maxItems,
        int totalNumItems,
        boolean hasMoreItems
    ) {
    }

    public record MutationRequest(
        String objectId,
        String path,
        String folderId,
        String folderPath,
        String name,
        String description,
        String mimeType,
        Long contentLength,
        Map<String, Object> properties,
        Map<String, Object> metadata
    ) {
    }

    public record MutationResponse(
        String repositoryId,
        String action,
        ObjectEntry object,
        String deletedObjectId,
        String message
    ) {
    }
}
