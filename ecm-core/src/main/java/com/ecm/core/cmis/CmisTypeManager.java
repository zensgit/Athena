package com.ecm.core.cmis;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.repository.AspectDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CmisTypeManager {

    private final AspectDefinitionRepository aspectDefinitionRepository;

    public List<CmisModels.TypeDefinition> getBaseTypes() {
        List<CmisModels.TypeDefinition> types = new ArrayList<>();
        types.add(new CmisModels.TypeDefinition(
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
        ));
        types.add(new CmisModels.TypeDefinition(
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
        ));
        types.add(new CmisModels.TypeDefinition(
            "cmis:secondary",
            "Secondary Type",
            "cmis:secondary",
            false,
            false,
            true,
            List.of()
        ));
        return types;
    }

    public List<CmisModels.TypeDefinition> getSecondaryTypes() {
        List<AspectDefinition> aspects = aspectDefinitionRepository.findAllActive();
        return aspects.stream()
            .map(aspect -> new CmisModels.TypeDefinition(
                aspect.qualifiedName(),
                aspect.getTitle() != null ? aspect.getTitle() : aspect.getName(),
                "cmis:secondary",
                false,
                false,
                true,
                List.of()
            ))
            .toList();
    }

    public List<CmisModels.TypeDefinition> getAllTypes() {
        List<CmisModels.TypeDefinition> all = new ArrayList<>(getBaseTypes());
        all.addAll(getSecondaryTypes());
        return all;
    }
}
