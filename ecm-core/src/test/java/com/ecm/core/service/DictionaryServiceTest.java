package com.ecm.core.service;

import com.ecm.core.entity.*;
import com.ecm.core.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryServiceTest {

    @Mock private TypeDefinitionRepository typeRepo;
    @Mock private AspectDefinitionRepository aspectRepo;
    @Mock private PropertyDefinitionRepository propertyRepo;
    @Mock private ContentModelDefinitionRepository modelRepo;
    @InjectMocks private DictionaryService service;

    @Nested
    @DisplayName("parseQualifiedName")
    class ParseQName {

        @Test
        void parsesValidQName() {
            String[] parts = service.parseQualifiedName("cm:content");
            assertEquals("cm", parts[0]);
            assertEquals("content", parts[1]);
        }

        @Test
        void rejectsBlankInput() {
            assertThrows(IllegalArgumentException.class, () -> service.parseQualifiedName(""));
        }

        @Test
        void rejectsNoColon() {
            assertThrows(IllegalArgumentException.class, () -> service.parseQualifiedName("content"));
        }

        @Test
        void rejectsLeadingColon() {
            assertThrows(IllegalArgumentException.class, () -> service.parseQualifiedName(":content"));
        }

        @Test
        void rejectsTrailingColon() {
            assertThrows(IllegalArgumentException.class, () -> service.parseQualifiedName("cm:"));
        }
    }

    @Nested
    @DisplayName("getType")
    class GetType {

        @Test
        void returnsTypeByQualifiedName() {
            TypeDefinition type = new TypeDefinition();
            type.setName("content");
            when(typeRepo.findByQualifiedName("cm", "content")).thenReturn(Optional.of(type));

            TypeDefinition result = service.getType("cm:content");
            assertEquals("content", result.getName());
        }

        @Test
        void throwsWhenTypeNotFound() {
            when(typeRepo.findByQualifiedName("cm", "missing")).thenReturn(Optional.empty());
            assertThrows(java.util.NoSuchElementException.class, () -> service.getType("cm:missing"));
        }
    }

    @Nested
    @DisplayName("getAspect")
    class GetAspect {

        @Test
        void returnsAspectByQualifiedName() {
            AspectDefinition aspect = new AspectDefinition();
            aspect.setName("titled");
            when(aspectRepo.findByQualifiedName("cm", "titled")).thenReturn(Optional.of(aspect));

            AspectDefinition result = service.getAspect("cm:titled");
            assertEquals("titled", result.getName());
        }
    }

    @Nested
    @DisplayName("getPropertiesForType with inheritance")
    class TypePropertyInheritance {

        @Test
        void resolvesSingleLevelProperties() {
            UUID typeId = UUID.randomUUID();
            TypeDefinition type = new TypeDefinition();
            type.setId(typeId);
            type.setName("content");

            PropertyDefinition prop = new PropertyDefinition();
            prop.setName("title");

            when(typeRepo.findByQualifiedName("cm", "content")).thenReturn(Optional.of(type));
            when(propertyRepo.findByTypeDefinitionId(typeId)).thenReturn(List.of(prop));

            List<PropertyDefinition> result = service.getPropertiesForType("cm:content");
            assertEquals(1, result.size());
            assertEquals("title", result.get(0).getName());
        }

        @Test
        void mergesParentProperties() {
            UUID parentId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();

            TypeDefinition parent = new TypeDefinition();
            parent.setId(parentId);
            parent.setName("base");

            TypeDefinition child = new TypeDefinition();
            child.setId(childId);
            child.setName("content");
            child.setParentName("cm:base");

            PropertyDefinition parentProp = new PropertyDefinition();
            parentProp.setName("name");

            PropertyDefinition childProp = new PropertyDefinition();
            childProp.setName("title");

            when(typeRepo.findByQualifiedName("cm", "content")).thenReturn(Optional.of(child));
            when(typeRepo.findByQualifiedName("cm", "base")).thenReturn(Optional.of(parent));
            when(propertyRepo.findByTypeDefinitionId(parentId)).thenReturn(List.of(parentProp));
            when(propertyRepo.findByTypeDefinitionId(childId)).thenReturn(List.of(childProp));

            List<PropertyDefinition> result = service.getPropertiesForType("cm:content");
            assertEquals(2, result.size());
        }

        @Test
        void childPropertyOverridesParent() {
            UUID parentId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();

            TypeDefinition parent = new TypeDefinition();
            parent.setId(parentId);
            parent.setName("base");

            TypeDefinition child = new TypeDefinition();
            child.setId(childId);
            child.setName("content");
            child.setParentName("cm:base");

            PropertyDefinition parentProp = new PropertyDefinition();
            parentProp.setName("title");
            parentProp.setMandatory(false);

            PropertyDefinition childProp = new PropertyDefinition();
            childProp.setName("title");
            childProp.setMandatory(true);

            when(typeRepo.findByQualifiedName("cm", "content")).thenReturn(Optional.of(child));
            when(typeRepo.findByQualifiedName("cm", "base")).thenReturn(Optional.of(parent));
            when(propertyRepo.findByTypeDefinitionId(parentId)).thenReturn(List.of(parentProp));
            when(propertyRepo.findByTypeDefinitionId(childId)).thenReturn(List.of(childProp));

            List<PropertyDefinition> result = service.getPropertiesForType("cm:content");
            assertEquals(1, result.size());
            assertTrue(result.get(0).isMandatory());
        }
    }

    @Nested
    @DisplayName("resolveTypeHierarchy")
    class TypeHierarchy {

        @Test
        void returnsRootToLeafOrder() {
            TypeDefinition root = new TypeDefinition();
            root.setName("base");
            root.setParentName(null);

            TypeDefinition child = new TypeDefinition();
            child.setName("content");
            child.setParentName("cm:base");

            when(typeRepo.findByQualifiedName("cm", "content")).thenReturn(Optional.of(child));
            when(typeRepo.findByQualifiedName("cm", "base")).thenReturn(Optional.of(root));

            List<String> hierarchy = service.resolveTypeHierarchy("cm:content");
            assertEquals(List.of("cm:base", "cm:content"), hierarchy);
        }
    }

    @Nested
    @DisplayName("getMandatoryAspectsForType")
    class MandatoryAspects {

        @Test
        void returnsMandatoryAspectsList() {
            TypeDefinition type = new TypeDefinition();
            type.setName("content");
            type.setMandatoryAspects(List.of("cm:auditable", "cm:titled"));

            when(typeRepo.findByQualifiedName("cm", "content")).thenReturn(Optional.of(type));

            List<String> result = service.getMandatoryAspectsForType("cm:content");
            assertEquals(List.of("cm:auditable", "cm:titled"), result);
        }

        @Test
        void returnsEmptyWhenNoMandatoryAspects() {
            TypeDefinition type = new TypeDefinition();
            type.setName("simple");
            type.setMandatoryAspects(null);

            when(typeRepo.findByQualifiedName("cm", "simple")).thenReturn(Optional.of(type));

            List<String> result = service.getMandatoryAspectsForType("cm:simple");
            assertTrue(result.isEmpty());
        }
    }
}
