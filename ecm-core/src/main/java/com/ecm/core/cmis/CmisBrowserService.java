package com.ecm.core.cmis;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CmisBrowserService {

    private final NodeService nodeService;
    private final FolderService folderService;
    private final CmisTypeManager typeManager;
    private final CmisObjectFactory objectFactory;
    private final VersionService versionService;

    public CmisModels.RepositoryInfo getRepositoryInfo() {
        return new CmisModels.RepositoryInfo(
            objectFactory.getRepositoryId(),
            "Athena Repository",
            "Athena",
            "Athena ECM",
            "1.0",
            "1.1",
            CmisObjectFactory.ROOT_OBJECT_ID,
            List.of(
                "read",
                "folder-tree",
                "path-addressing",
                "type-children"
            )
        );
    }

    public CmisModels.TypeChildrenResponse getTypeChildren() {
        List<CmisModels.TypeDefinition> types = typeManager.getAllTypes();
        return new CmisModels.TypeChildrenResponse(types, types.size(), false);
    }

    public CmisModels.ObjectEntry getObject(String objectId, String path) {
        if (path != null && !path.isBlank()) {
            if ("/".equals(path.trim())) {
                return objectFactory.rootObject();
            }
            return objectFactory.fromNode(nodeService.getNodeByPath(path.trim()));
        }
        if (objectId == null || objectId.isBlank() || CmisObjectFactory.ROOT_OBJECT_ID.equalsIgnoreCase(objectId.trim())) {
            return objectFactory.rootObject();
        }
        return objectFactory.fromNode(nodeService.getNode(UUID.fromString(objectId.trim())));
    }

    public CmisModels.ChildrenResponse getChildren(String objectId, String path, int skipCount, int maxItems) {
        int normalizedSkip = Math.max(skipCount, 0);
        int normalizedMax = Math.max(Math.min(maxItems, 200), 1);

        if ((path != null && "/".equals(path.trim()))
            || CmisObjectFactory.ROOT_OBJECT_ID.equalsIgnoreCase(defaultSelectorTarget(objectId))) {
            List<CmisModels.ObjectEntry> rootChildren = folderService.getRootFolders().stream()
                .skip(normalizedSkip)
                .limit(normalizedMax)
                .map(objectFactory::fromNode)
                .toList();
            int total = folderService.getRootFolders().size();
            return new CmisModels.ChildrenResponse(
                objectFactory.getRepositoryId(),
                CmisObjectFactory.ROOT_OBJECT_ID,
                rootChildren,
                normalizedSkip,
                normalizedMax,
                total,
                normalizedSkip + rootChildren.size() < total
            );
        }

        Node parent = resolveNode(objectId, path);
        if (!parent.isFolder()) {
            throw new IllegalArgumentException("Children can only be listed for folders");
        }

        List<Node> allChildren = nodeService.getChildren(parent.getId(), Pageable.unpaged()).getContent();
        List<CmisModels.ObjectEntry> entries = allChildren.stream()
            .skip(normalizedSkip)
            .limit(normalizedMax)
            .map(objectFactory::fromNode)
            .toList();
        return new CmisModels.ChildrenResponse(
            objectFactory.getRepositoryId(),
            parent.getId().toString(),
            entries,
            normalizedSkip,
            normalizedMax,
            allChildren.size(),
            normalizedSkip + entries.size() < allChildren.size()
        );
    }

    public List<CmisModels.ObjectEntry> getAllVersions(String objectId) {
        UUID documentId = UUID.fromString(objectId.trim());
        Node node = nodeService.getNode(documentId);
        if (!(node instanceof Document document)) {
            throw new IllegalArgumentException("getAllVersions requires a document, not a folder");
        }
        List<Version> versions = versionService.getVersionHistory(documentId);
        if (versions.isEmpty()) {
            return List.of(objectFactory.fromNode(document));
        }
        int latestVersionNumber = versions.stream()
            .mapToInt(Version::getVersionNumber)
            .max()
            .orElse(0);
        return versions.stream()
            .map(v -> versionToObjectEntry(v, document, latestVersionNumber))
            .toList();
    }

    public CmisModels.ObjectEntry getLatestVersion(String objectId, boolean major) {
        UUID documentId = UUID.fromString(objectId.trim());
        Node node = nodeService.getNode(documentId);
        if (!(node instanceof Document document)) {
            throw new IllegalArgumentException("getLatestVersion requires a document, not a folder");
        }
        List<Version> versions = versionService.getVersionHistory(documentId, major);
        if (versions.isEmpty()) {
            return objectFactory.fromNode(document);
        }
        int latestVersionNumber = versions.stream()
            .mapToInt(Version::getVersionNumber)
            .max()
            .orElse(0);
        return versionToObjectEntry(versions.get(0), document, latestVersionNumber);
    }

    private CmisModels.ObjectEntry versionToObjectEntry(Version version, Document document, int latestVersionNumber) {
        Map<String, Object> properties = new LinkedHashMap<>();
        String versionedObjectId = document.getId().toString() + ";v" + version.getVersionString();
        properties.put("cmis:objectId", versionedObjectId);
        properties.put("cmis:name", document.getName());
        properties.put("cmis:baseTypeId", "cmis:document");
        properties.put("cmis:objectTypeId", "cmis:document");
        properties.put("cmis:versionLabel", version.getVersionString());
        properties.put("cmis:versionSeriesId", document.getId().toString());
        properties.put("cmis:isLatestVersion", version.getVersionNumber().equals(latestVersionNumber));
        properties.put("cmis:isMajorVersion", version.isMajorVersionFlag());
        properties.put("cmis:creationDate", version.getFrozenDate());
        properties.put("cmis:createdBy", version.getFrozenBy());
        properties.put("cmis:contentStreamMimeType", version.getMimeType());
        properties.put("cmis:contentStreamLength", version.getFileSize());
        properties.put("cmis:checkinComment", version.getComment());

        List<String> allowableActions = List.of(
            "canGetProperties", "canGetContentStream", "canGetAllVersions"
        );

        return new CmisModels.ObjectEntry(
            objectFactory.getRepositoryId(),
            versionedObjectId,
            document.getName(),
            "cmis:document",
            "cmis:document",
            document.getPath(),
            document.getParent() != null && document.getParent().getId() != null
                ? document.getParent().getId().toString()
                : CmisObjectFactory.ROOT_OBJECT_ID,
            false,
            properties,
            allowableActions
        );
    }

    private Node resolveNode(String objectId, String path) {
        if (path != null && !path.isBlank()) {
            return nodeService.getNodeByPath(path.trim());
        }
        String normalizedObjectId = defaultSelectorTarget(objectId);
        if (CmisObjectFactory.ROOT_OBJECT_ID.equalsIgnoreCase(normalizedObjectId)) {
            Folder virtualRoot = new Folder();
            virtualRoot.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            virtualRoot.setName("Company Home");
            virtualRoot.setPath("/");
            return virtualRoot;
        }
        return nodeService.getNode(UUID.fromString(normalizedObjectId));
    }

    private String defaultSelectorTarget(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            return CmisObjectFactory.ROOT_OBJECT_ID;
        }
        return objectId.trim().toLowerCase(Locale.ROOT);
    }
}
