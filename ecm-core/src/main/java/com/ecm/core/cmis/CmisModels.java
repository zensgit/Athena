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
        Map<String, Object> metadata,
        String filename,
        String contentBase64,
        String comment,
        Boolean majorVersion,
        Boolean keepCheckedOut
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

    public record ContentChangesResponse(
        List<ChangeEntry> changes,
        String latestChangeLogToken,
        boolean hasMoreItems
    ) {
    }

    public record ChangeEntry(
        String objectId,
        String changeType,
        String changeTime,
        String user
    ) {
    }

    public record AclResponse(
        String objectId,
        List<AceEntry> aces,
        boolean isExact
    ) {
    }

    public record AceEntry(
        String principalId,
        List<String> permissions,
        boolean isDirect
    ) {
    }

    public record ApplyAclRequest(
        String objectId,
        List<AceEntry> addAces,
        List<AceEntry> removeAces
    ) {
    }

    public record RelationshipsResponse(
        String objectId,
        List<RelationshipEntry> relationships
    ) {
    }

    public record RelationshipEntry(
        String relationshipId,
        String sourceId,
        String targetId,
        String relationshipType,
        String createdAt
    ) {
    }

    public record CreateRelationshipRequest(
        String sourceId,
        String targetId,
        String relationshipType
    ) {
    }

    public record RenditionsResponse(
        String objectId,
        List<RenditionEntry> renditions
    ) {
    }

    public record RenditionEntry(
        String streamId,
        String kind,
        String mimeType,
        String title,
        String contentUrl
    ) {
    }
}
