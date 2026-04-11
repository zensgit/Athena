package com.ecm.core.cmis;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CmisMutationService {

    private final FolderService folderService;
    private final NodeService nodeService;
    private final CmisObjectFactory objectFactory;

    public CmisModels.MutationResponse createFolder(CmisModels.MutationRequest request) {
        String folderName = requireText(request.name(), "Folder name is required");
        UUID parentId = resolveFolderTarget(request.folderId(), request.folderPath());
        Folder folder = folderService.createFolder(new FolderService.CreateFolderRequest(
            folderName,
            blankToNull(request.description()),
            parentId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "createFolder",
            objectFactory.fromNode(folder),
            null,
            "Folder created"
        );
    }

    public CmisModels.MutationResponse createDocument(CmisModels.MutationRequest request) {
        String documentName = requireText(request.name(), "Document name is required");
        UUID parentId = resolveFolderTarget(request.folderId(), request.folderPath());
        String mimeType = blankToNull(request.mimeType()) != null ? request.mimeType().trim() : "application/octet-stream";
        long contentLength = request.contentLength() != null && request.contentLength() >= 0 ? request.contentLength() : 0L;

        Document document = nodeService.createDocument(documentName, mimeType, contentLength, parentId);

        Map<String, Object> updates = new LinkedHashMap<>();
        if (blankToNull(request.description()) != null) {
            updates.put("description", request.description().trim());
        }
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            updates.put("metadata", request.metadata());
        }
        if (request.properties() != null && !request.properties().isEmpty()) {
            updates.put("properties", request.properties());
        }
        if (!updates.isEmpty()) {
            document = (Document) nodeService.updateNode(document.getId(), updates);
        }

        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "createDocument",
            objectFactory.fromNode(document),
            null,
            "Document created"
        );
    }

    public CmisModels.MutationResponse updateProperties(CmisModels.MutationRequest request) {
        Node node = resolveNode(request.objectId(), request.path());
        Map<String, Object> updates = new LinkedHashMap<>();

        if (request.properties() != null) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            Map<String, Object> customProperties = new LinkedHashMap<>();
            request.properties().forEach((key, value) -> {
                if ("cmis:name".equals(key)) {
                    updates.put("name", requirePropertyText(value, "cmis:name"));
                } else if ("cmis:description".equals(key)) {
                    updates.put("description", value != null ? value.toString() : null);
                } else if (key != null && key.startsWith("athena:metadata.")) {
                    metadata.put(key.substring("athena:metadata.".length()), value);
                } else if (key != null && key.startsWith("athena:property.")) {
                    customProperties.put(key.substring("athena:property.".length()), value);
                }
            });
            if (!metadata.isEmpty()) {
                updates.put("metadata", metadata);
            }
            if (!customProperties.isEmpty()) {
                updates.put("properties", customProperties);
            }
        }

        if (request.metadata() != null && !request.metadata().isEmpty()) {
            updates.merge("metadata", request.metadata(), (existing, incoming) -> {
                Map<String, Object> merged = new LinkedHashMap<>();
                merged.putAll((Map<String, Object>) existing);
                merged.putAll((Map<String, Object>) incoming);
                return merged;
            });
        }

        if (updates.isEmpty()) {
            throw new IllegalArgumentException("At least one updatable property is required");
        }

        Node updated = nodeService.updateNode(node.getId(), updates);
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "updateProperties",
            objectFactory.fromNode(updated),
            null,
            "Properties updated"
        );
    }

    public CmisModels.MutationResponse deleteObject(CmisModels.MutationRequest request) {
        Node node = resolveNode(request.objectId(), request.path());
        nodeService.deleteNode(node.getId(), false);
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "deleteObject",
            null,
            node.getId().toString(),
            "Object deleted"
        );
    }

    private Node resolveNode(String objectId, String path) {
        if (path != null && !path.isBlank()) {
            return nodeService.getNodeByPath(path.trim());
        }
        if (objectId == null || objectId.isBlank() || CmisObjectFactory.ROOT_OBJECT_ID.equalsIgnoreCase(objectId.trim())) {
            throw new IllegalArgumentException("A non-root objectId or path is required");
        }
        return nodeService.getNode(UUID.fromString(objectId.trim()));
    }

    private UUID resolveFolderTarget(String folderId, String folderPath) {
        if (folderPath != null && !folderPath.isBlank()) {
            String normalized = folderPath.trim();
            if ("/".equals(normalized)) {
                return null;
            }
            return folderService.getFolderByPath(normalized).getId();
        }
        if (folderId == null || folderId.isBlank() || CmisObjectFactory.ROOT_OBJECT_ID.equalsIgnoreCase(folderId.trim())) {
            return null;
        }
        return folderService.getFolder(UUID.fromString(folderId.trim())).getId();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String requirePropertyText(Object value, String propertyName) {
        if (value == null) {
            throw new IllegalArgumentException(propertyName + " is required");
        }
        return requireText(value.toString(), propertyName + " is required");
    }
}
