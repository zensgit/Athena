package com.ecm.core.cmis;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CmisBrowserService {

    private final NodeService nodeService;
    private final FolderService folderService;
    private final CmisTypeManager typeManager;
    private final CmisObjectFactory objectFactory;

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
        List<CmisModels.TypeDefinition> types = typeManager.getBaseTypes();
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
