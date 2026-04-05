package com.ecm.core.cmis;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CmisTypeManager {

    public List<CmisModels.TypeDefinition> getBaseTypes() {
        return List.of(
            new CmisModels.TypeDefinition(
                "cmis:folder",
                "Folder",
                "cmis:folder",
                true,
                true,
                true,
                List.of(
                    "cmis:objectId",
                    "cmis:name",
                    "cmis:baseTypeId",
                    "cmis:objectTypeId",
                    "cmis:path",
                    "cmis:parentId"
                )
            ),
            new CmisModels.TypeDefinition(
                "cmis:document",
                "Document",
                "cmis:document",
                true,
                true,
                true,
                List.of(
                    "cmis:objectId",
                    "cmis:name",
                    "cmis:baseTypeId",
                    "cmis:objectTypeId",
                    "cmis:path",
                    "cmis:parentId",
                    "cmis:contentStreamMimeType",
                    "cmis:contentStreamLength",
                    "cmis:versionLabel"
                )
            )
        );
    }
}
