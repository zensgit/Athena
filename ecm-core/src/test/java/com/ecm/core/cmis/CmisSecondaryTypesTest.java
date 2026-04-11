package com.ecm.core.cmis;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.Folder;
import com.ecm.core.repository.AspectDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CmisSecondaryTypesTest {

    private final CmisObjectFactory objectFactory = new CmisObjectFactory();

    @Test
    void fromNode_exposesSecondaryObjectTypeIdsFromAspects() {
        Folder folder = newFolder("TestFolder");
        folder.addAspect("cm:titled");
        folder.addAspect("cm:auditable");

        CmisModels.ObjectEntry entry = objectFactory.fromNode(folder);

        @SuppressWarnings("unchecked")
        List<String> secondaryTypeIds = (List<String>) entry.properties().get("cmis:secondaryObjectTypeIds");
        assertThat(secondaryTypeIds).containsExactly("cm:auditable", "cm:titled");
    }

    @Test
    void fromNode_emptyAspects_returnsEmptyList() {
        Folder folder = newFolder("EmptyAspects");

        CmisModels.ObjectEntry entry = objectFactory.fromNode(folder);

        @SuppressWarnings("unchecked")
        List<String> secondaryTypeIds = (List<String>) entry.properties().get("cmis:secondaryObjectTypeIds");
        assertThat(secondaryTypeIds).isNotNull().isEmpty();
    }

    @Test
    void fromNode_nullAspects_returnsEmptyList() {
        Folder folder = newFolder("NullAspects");
        folder.setAspects(null);

        CmisModels.ObjectEntry entry = objectFactory.fromNode(folder);

        @SuppressWarnings("unchecked")
        List<String> secondaryTypeIds = (List<String>) entry.properties().get("cmis:secondaryObjectTypeIds");
        assertThat(secondaryTypeIds).isNotNull().isEmpty();
    }

    @Test
    void typeManager_baseTypes_includesCmisSecondary() {
        AspectDefinitionRepository repo = mock(AspectDefinitionRepository.class);
        when(repo.findAllActive()).thenReturn(List.of());

        CmisTypeManager typeManager = new CmisTypeManager(repo);
        List<CmisModels.TypeDefinition> baseTypes = typeManager.getBaseTypes();

        assertThat(baseTypes).extracting(CmisModels.TypeDefinition::id)
            .contains("cmis:folder", "cmis:document", "cmis:secondary");

        CmisModels.TypeDefinition secondary = baseTypes.stream()
            .filter(t -> "cmis:secondary".equals(t.id()))
            .findFirst()
            .orElseThrow();
        assertThat(secondary.creatable()).isFalse();
        assertThat(secondary.fileable()).isFalse();
        assertThat(secondary.queryable()).isTrue();
    }

    @Test
    void typeManager_secondaryTypes_fromAspectDefinitions() {
        AspectDefinitionRepository repo = mock(AspectDefinitionRepository.class);

        ContentModelDefinition model = new ContentModelDefinition();
        model.setPrefix("cm");

        AspectDefinition titled = new AspectDefinition();
        titled.setName("titled");
        titled.setTitle("Titled");
        titled.setModel(model);

        AspectDefinition auditable = new AspectDefinition();
        auditable.setName("auditable");
        auditable.setTitle(null);
        auditable.setModel(model);

        when(repo.findAllActive()).thenReturn(List.of(titled, auditable));

        CmisTypeManager typeManager = new CmisTypeManager(repo);
        List<CmisModels.TypeDefinition> secondary = typeManager.getSecondaryTypes();

        assertThat(secondary).hasSize(2);
        assertThat(secondary).extracting(CmisModels.TypeDefinition::id)
            .containsExactly("cm:titled", "cm:auditable");
        assertThat(secondary).extracting(CmisModels.TypeDefinition::baseTypeId)
            .containsOnly("cmis:secondary");
        assertThat(secondary).extracting(CmisModels.TypeDefinition::displayName)
            .containsExactly("Titled", "auditable");
    }

    @Test
    void typeManager_allTypes_combinesBaseAndSecondary() {
        AspectDefinitionRepository repo = mock(AspectDefinitionRepository.class);

        ContentModelDefinition model = new ContentModelDefinition();
        model.setPrefix("custom");

        AspectDefinition aspect = new AspectDefinition();
        aspect.setName("tagged");
        aspect.setTitle("Tagged");
        aspect.setModel(model);

        when(repo.findAllActive()).thenReturn(List.of(aspect));

        CmisTypeManager typeManager = new CmisTypeManager(repo);
        List<CmisModels.TypeDefinition> all = typeManager.getAllTypes();

        assertThat(all).extracting(CmisModels.TypeDefinition::id)
            .contains("cmis:folder", "cmis:document", "cmis:secondary", "custom:tagged");
    }

    private Folder newFolder(String name) {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName(name);
        folder.setPath("/" + name);
        folder.setAspects(new HashSet<>());
        return folder;
    }
}
