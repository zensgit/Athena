package com.ecm.core.cmis;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CmisObjectFactory {

    public static final String ROOT_OBJECT_ID = "root";

    private final RepositoryIdentityProvider repositoryIdentityProvider;

    public CmisObjectFactory() {
        this(new RepositoryIdentityProvider(
            RepositoryIdentityProvider.DEFAULT_CMIS_REPOSITORY_ID,
            RepositoryIdentityProvider.DEFAULT_CMIS_REPOSITORY_ID
        ));
    }

    public String getRepositoryId() {
        return repositoryIdentityProvider.getCmisRepositoryId();
    }

    public CmisModels.ObjectEntry rootObject() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("cmis:objectId", ROOT_OBJECT_ID);
        properties.put("cmis:name", "Company Home");
        properties.put("cmis:baseTypeId", "cmis:folder");
        properties.put("cmis:objectTypeId", "cmis:folder");
        properties.put("cmis:path", "/");
        properties.put("cmis:parentId", null);

        return new CmisModels.ObjectEntry(
            getRepositoryId(),
            ROOT_OBJECT_ID,
            "Company Home",
            "cmis:folder",
            "cmis:folder",
            "/",
            null,
            true,
            properties,
            List.of("canGetFolderTree", "canGetDescendants", "canGetChildren")
        );
    }

    public CmisModels.ObjectEntry fromNode(Node node) {
        String baseTypeId = node.isFolder() ? "cmis:folder" : "cmis:document";
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("cmis:objectId", node.getId().toString());
        properties.put("cmis:name", node.getName());
        properties.put("cmis:baseTypeId", baseTypeId);
        properties.put("cmis:objectTypeId", baseTypeId);
        properties.put("cmis:path", node.getPath());
        properties.put("cmis:parentId", node.getParent() != null && node.getParent().getId() != null
            ? node.getParent().getId().toString()
            : ROOT_OBJECT_ID);
        properties.put("cmis:createdBy", node.getCreatedBy());
        properties.put("cmis:creationDate", node.getCreatedDate());
        properties.put("cmis:lastModifiedBy", node.getLastModifiedBy());
        properties.put("cmis:lastModificationDate", node.getLastModifiedDate());
        properties.put("athena:typeQName", node.getTypeQName());
        properties.put("athena:archiveStatus", node.getArchiveStatus() != null ? node.getArchiveStatus().name() : null);

        // Secondary types from aspects
        List<String> secondaryTypeIds = new ArrayList<>();
        if (node.getAspects() != null) {
            secondaryTypeIds.addAll(node.getAspects().stream().sorted().toList());
        }
        properties.put("cmis:secondaryObjectTypeIds", secondaryTypeIds);

        if (node instanceof Document document) {
            properties.put("cmis:contentStreamMimeType", document.getMimeType());
            properties.put("cmis:contentStreamLength", document.getFileSize());
            properties.put("cmis:isVersionSeriesCheckedOut", document.isCheckedOut());
            properties.put("cmis:versionLabel", document.getVersionLabel() != null ? document.getVersionLabel() : document.getVersionString());
            properties.put("cmis:isLatestVersion", true);
            properties.put("cmis:isMajorVersion", document.getCurrentVersion() == null || document.getCurrentVersion().isMajorVersionFlag());
            properties.put("athena:checkoutUser", document.getCheckoutUser());
            properties.put("athena:checkoutDate", document.getCheckoutDate());
            properties.put("athena:workingCopy", document.isWorkingCopy());
            properties.put("athena:workingCopyOf", document.getWorkingCopyOf() != null ? document.getWorkingCopyOf().toString() : null);
            properties.put("athena:contentId", document.getContentId());
            properties.put("athena:contentHash", document.getContentHash());
        }

        List<String> allowableActions = node.isFolder()
            ? new ArrayList<>(List.of("canGetProperties", "canGetChildren", "canGetObjectParents"))
            : new ArrayList<>(List.of("canGetProperties", "canGetContentStream", "canGetObjectParents", "canSetContentStream"));
        if (node instanceof Document document) {
            if (document.isCheckedOut()) {
                allowableActions.add("canCheckIn");
                allowableActions.add("canCancelCheckOut");
            } else {
                allowableActions.add("canCheckOut");
            }
        }

        return new CmisModels.ObjectEntry(
            getRepositoryId(),
            node.getId().toString(),
            node.getName(),
            baseTypeId,
            baseTypeId,
            node.getPath(),
            node.getParent() != null && node.getParent().getId() != null ? node.getParent().getId().toString() : ROOT_OBJECT_ID,
            false,
            properties,
            allowableActions
        );
    }
}
