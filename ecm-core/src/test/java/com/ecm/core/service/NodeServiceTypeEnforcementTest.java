package com.ecm.core.service;

import com.ecm.core.entity.*;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.exception.PropertyValidationException;
import com.ecm.core.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeServiceTypeEnforcementTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private CorrespondentRepository correspondentRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ContentReferenceService contentReferenceService;
    @Mock private DictionaryService dictionaryService;

    private PropertyConstraintValidator constraintValidator;
    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        constraintValidator = new PropertyConstraintValidator();
        nodeService = new NodeService(
            nodeRepository, folderRepository, documentRepository,
            permissionRepository, correspondentRepository, securityService, eventPublisher, contentReferenceService
        );
        ReflectionTestUtils.setField(nodeService, "dictionaryService", dictionaryService);
        ReflectionTestUtils.setField(nodeService, "propertyConstraintValidator", constraintValidator);
    }

    // ================================================================= type defaults

    @Nested
    @DisplayName("enforceTypeProperties — defaults")
    class TypeDefaults {

        @Test
        @DisplayName("applies default values from type definition")
        void appliesDefaults() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:folder");
            folder.setProperties(new HashMap<>());

            PropertyDefinition iconProp = typePropDef("icon", false, "folder.png");
            when(dictionaryService.getPropertiesForType("cm:folder")).thenReturn(List.of(iconProp));

            nodeService.enforceTypeProperties(folder);

            assertEquals("folder.png", folder.getProperties().get("cm:icon"));
        }

        @Test
        @DisplayName("does not overwrite existing property with default")
        void preservesExisting() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:folder");
            folder.setProperties(new HashMap<>(Map.of("cm:icon", "custom.png")));

            PropertyDefinition iconProp = typePropDef("icon", false, "folder.png");
            when(dictionaryService.getPropertiesForType("cm:folder")).thenReturn(List.of(iconProp));

            nodeService.enforceTypeProperties(folder);

            assertEquals("custom.png", folder.getProperties().get("cm:icon"));
        }
    }

    // ================================================================= type mandatory

    @Nested
    @DisplayName("enforceTypeProperties — mandatory")
    class TypeMandatory {

        @Test
        @DisplayName("rejects missing mandatory type property")
        void rejectsMissing() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:folder");
            folder.setProperties(new HashMap<>());

            PropertyDefinition nameProp = typePropDef("title", true, null);
            when(dictionaryService.getPropertiesForType("cm:folder")).thenReturn(List.of(nameProp));

            PropertyValidationException ex = assertThrows(PropertyValidationException.class,
                () -> nodeService.enforceTypeProperties(folder));
            assertTrue(ex.getMessage().contains("mandatory"));
            assertTrue(ex.getViolations().get(0).contains("cm:title"));
        }

        @Test
        @DisplayName("passes when mandatory property has default applied")
        void passesMandatoryWithDefault() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:folder");
            folder.setProperties(new HashMap<>());

            PropertyDefinition titleProp = typePropDef("title", true, "Default Title");
            when(dictionaryService.getPropertiesForType("cm:folder")).thenReturn(List.of(titleProp));

            assertDoesNotThrow(() -> nodeService.enforceTypeProperties(folder));
            assertEquals("Default Title", folder.getProperties().get("cm:title"));
        }

        @Test
        @DisplayName("passes when mandatory property is provided by caller")
        void passesMandatoryProvided() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:folder");
            folder.setProperties(new HashMap<>(Map.of("cm:title", "My Folder")));

            PropertyDefinition titleProp = typePropDef("title", true, null);
            when(dictionaryService.getPropertiesForType("cm:folder")).thenReturn(List.of(titleProp));

            assertDoesNotThrow(() -> nodeService.enforceTypeProperties(folder));
        }
    }

    // ================================================================= type constraints

    @Nested
    @DisplayName("enforceTypeProperties — constraints")
    class TypeConstraints {

        @Test
        @DisplayName("rejects value failing LIST constraint on type property")
        void rejectsListViolation() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:classifiable");
            folder.setProperties(new HashMap<>(Map.of("cm:category", "INVALID")));

            PropertyDefinition catProp = typePropDef("category", false, null);
            ConstraintDefinition listC = new ConstraintDefinition();
            listC.setConstraintType(ConstraintType.LIST);
            listC.setParameters(Map.of("allowedValues", List.of("A", "B", "C")));
            catProp.setConstraints(List.of(listC));
            when(dictionaryService.getPropertiesForType("cm:classifiable")).thenReturn(List.of(catProp));

            assertThrows(PropertyValidationException.class,
                () -> nodeService.enforceTypeProperties(folder));
        }

        @Test
        @DisplayName("passes valid constraint")
        void passesValid() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:classifiable");
            folder.setProperties(new HashMap<>(Map.of("cm:category", "A")));

            PropertyDefinition catProp = typePropDef("category", false, null);
            ConstraintDefinition listC = new ConstraintDefinition();
            listC.setConstraintType(ConstraintType.LIST);
            listC.setParameters(Map.of("allowedValues", List.of("A", "B", "C")));
            catProp.setConstraints(List.of(listC));
            when(dictionaryService.getPropertiesForType("cm:classifiable")).thenReturn(List.of(catProp));

            assertDoesNotThrow(() -> nodeService.enforceTypeProperties(folder));
        }
    }

    // ================================================================= mandatory aspects

    @Nested
    @DisplayName("applyMandatoryAspects")
    class MandatoryAspects {

        @Test
        @DisplayName("auto-attaches mandatory aspects declared on type")
        void autoAttachesMandatoryAspects() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:content");
            folder.setProperties(new HashMap<>());

            when(dictionaryService.getMandatoryAspectsForType("cm:content"))
                .thenReturn(List.of("cm:auditable"));
            // cm:auditable has a default
            PropertyDefinition auditProp = aspectPropDef("auditDate", false, "2026-01-01");
            when(dictionaryService.getPropertiesForAspect("cm:auditable")).thenReturn(List.of(auditProp));

            nodeService.applyMandatoryAspects(folder);

            assertTrue(folder.hasAspect("cm:auditable"));
            assertEquals("2026-01-01", folder.getProperties().get("cm:auditDate"));
        }

        @Test
        @DisplayName("does not duplicate already-attached aspects")
        void doesNotDuplicate() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:content");
            folder.addAspect("cm:auditable");
            folder.setProperties(new HashMap<>(Map.of("cm:auditDate", "2025-12-31")));

            when(dictionaryService.getMandatoryAspectsForType("cm:content"))
                .thenReturn(List.of("cm:auditable"));

            nodeService.applyMandatoryAspects(folder);

            // should not overwrite existing value
            assertEquals("2025-12-31", folder.getProperties().get("cm:auditDate"));
        }
    }

    // ================================================================= unmanaged type

    @Nested
    @DisplayName("unmanaged type")
    class Unmanaged {

        @Test
        @DisplayName("skips enforcement when no typeQName set")
        void skipsNoType() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>());

            assertDoesNotThrow(() -> nodeService.enforceTypeProperties(folder));
        }

        @Test
        @DisplayName("skips enforcement for unregistered type")
        void skipsUnregistered() {
            Folder folder = folder("test");
            folder.setTypeQName("custom:unknown");
            folder.setProperties(new HashMap<>());

            when(dictionaryService.getPropertiesForType("custom:unknown"))
                .thenThrow(new NoSuchElementException("Type not found"));

            assertDoesNotThrow(() -> nodeService.enforceTypeProperties(folder));
        }
    }

    // ================================================================= createNode integration

    @Nested
    @DisplayName("createNode wiring")
    class CreateNodeWiring {

        @Test
        @DisplayName("createNode enforces type properties before save")
        void enforceOnCreate() {
            Folder parent = folder("parent");
            when(folderRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
            when(securityService.hasPermission(parent, PermissionType.CREATE_CHILDREN)).thenReturn(true);
            when(nodeRepository.findByParentIdAndName(parent.getId(), "child")).thenReturn(Optional.empty());

            Folder child = new Folder();
            child.setName("child");
            child.setPath("/parent/child");
            child.setTypeQName("cm:folder");
            child.setProperties(new HashMap<>());

            PropertyDefinition mandatoryProp = typePropDef("title", true, null);
            when(dictionaryService.getPropertiesForType("cm:folder")).thenReturn(List.of(mandatoryProp));
            when(dictionaryService.getMandatoryAspectsForType("cm:folder")).thenReturn(List.of());

            assertThrows(PropertyValidationException.class,
                () -> nodeService.createNode(child, parent.getId()));
            verify(nodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("createNode auto-attaches mandatory aspects and enforces")
        void mandatoryAspectsOnCreate() {
            Folder parent = folder("parent");
            when(folderRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
            when(securityService.hasPermission(parent, PermissionType.CREATE_CHILDREN)).thenReturn(true);
            when(nodeRepository.findByParentIdAndName(parent.getId(), "child")).thenReturn(Optional.empty());
            when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Folder child = new Folder();
            child.setName("child");
            child.setPath("/parent/child");
            child.setTypeQName("cm:content");
            child.setProperties(new HashMap<>(Map.of("cm:title", "Report")));

            // type has no own mandatory props but requires cm:auditable aspect
            when(dictionaryService.getPropertiesForType("cm:content")).thenReturn(List.of());
            when(dictionaryService.getMandatoryAspectsForType("cm:content"))
                .thenReturn(List.of("cm:auditable"));
            PropertyDefinition auditProp = aspectPropDef("auditDate", false, "2026-01-01");
            when(dictionaryService.getPropertiesForAspect("cm:auditable")).thenReturn(List.of(auditProp));

            Node result = nodeService.createNode(child, parent.getId());

            assertTrue(result.hasAspect("cm:auditable"));
            assertEquals("2026-01-01", result.getProperties().get("cm:auditDate"));
        }
    }

    // ================================================================= updateNode integration

    @Nested
    @DisplayName("updateNode wiring")
    class UpdateNodeWiring {

        @Test
        @DisplayName("updateNode enforces type constraints after property merge")
        void enforceOnUpdate() {
            Folder folder = folder("test");
            folder.setTypeQName("cm:classifiable");
            folder.setProperties(new HashMap<>(Map.of("cm:category", "A")));
            when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), Node.ArchiveStatus.LIVE))
                .thenReturn(Optional.of(folder));
            when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
            when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(true);

            PropertyDefinition catProp = typePropDef("category", false, null);
            ConstraintDefinition listC = new ConstraintDefinition();
            listC.setConstraintType(ConstraintType.LIST);
            listC.setParameters(Map.of("allowedValues", List.of("A", "B", "C")));
            catProp.setConstraints(List.of(listC));
            when(dictionaryService.getPropertiesForType("cm:classifiable")).thenReturn(List.of(catProp));

            Map<String, Object> updates = new HashMap<>(Map.of("properties", Map.of("cm:category", "INVALID")));

            assertThrows(PropertyValidationException.class,
                () -> nodeService.updateNode(folder.getId(), updates));
            verify(nodeRepository, never()).save(any());
        }
    }

    // ================================================================= helpers

    private Folder folder(String name) {
        Folder f = new Folder();
        f.setId(UUID.randomUUID());
        f.setName(name);
        f.setPath("/" + name);
        f.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return f;
    }

    private PropertyDefinition typePropDef(String name, boolean mandatory, String defaultValue) {
        PropertyDefinition def = new PropertyDefinition();
        def.setId(UUID.randomUUID());
        def.setName(name);
        def.setDataType(PropertyDataType.TEXT);
        def.setMandatory(mandatory);
        def.setDefaultValue(defaultValue);

        ContentModelDefinition model = new ContentModelDefinition();
        model.setPrefix("cm");
        TypeDefinition type = new TypeDefinition();
        type.setModel(model);
        def.setTypeDefinition(type);

        return def;
    }

    private PropertyDefinition aspectPropDef(String name, boolean mandatory, String defaultValue) {
        PropertyDefinition def = new PropertyDefinition();
        def.setId(UUID.randomUUID());
        def.setName(name);
        def.setDataType(PropertyDataType.TEXT);
        def.setMandatory(mandatory);
        def.setDefaultValue(defaultValue);

        ContentModelDefinition model = new ContentModelDefinition();
        model.setPrefix("cm");
        AspectDefinition aspect = new AspectDefinition();
        aspect.setModel(model);
        def.setAspectDefinition(aspect);

        return def;
    }
}
