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
class NodeServicePropertyEnforcementTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private CorrespondentRepository correspondentRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DictionaryService dictionaryService;

    private PropertyConstraintValidator constraintValidator;
    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        constraintValidator = new PropertyConstraintValidator();
        nodeService = new NodeService(
            nodeRepository, folderRepository, documentRepository,
            permissionRepository, correspondentRepository, securityService, eventPublisher
        );
        ReflectionTestUtils.setField(nodeService, "dictionaryService", dictionaryService);
        ReflectionTestUtils.setField(nodeService, "propertyConstraintValidator", constraintValidator);
    }

    // ================================================================= addAspect enforcement

    @Nested
    @DisplayName("addAspect enforcement")
    class AddAspectEnforcement {

        @Test
        @DisplayName("applies default property values from aspect definition")
        void appliesDefaults() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>());
            stubWritable(folder);

            PropertyDefinition titleProp = propDef("title", false, "Untitled");
            when(dictionaryService.getPropertiesForAspect("cm:titled")).thenReturn(List.of(titleProp));

            nodeService.addAspect(folder.getId(), "cm:titled");

            assertEquals("Untitled", folder.getProperties().get("cm:title"));
        }

        @Test
        @DisplayName("does not overwrite existing property with default")
        void preservesExistingValues() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>(Map.of("cm:title", "My Title")));
            stubWritable(folder);

            PropertyDefinition titleProp = propDef("title", false, "Untitled");
            when(dictionaryService.getPropertiesForAspect("cm:titled")).thenReturn(List.of(titleProp));

            nodeService.addAspect(folder.getId(), "cm:titled");

            assertEquals("My Title", folder.getProperties().get("cm:title"));
        }

        @Test
        @DisplayName("rejects addAspect when mandatory property is missing and has no default")
        void rejectsMissingMandatory() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>());
            stubReadWrite(folder);

            PropertyDefinition mandatoryProp = propDef("title", true, null);
            when(dictionaryService.getPropertiesForAspect("cm:titled")).thenReturn(List.of(mandatoryProp));

            PropertyValidationException ex = assertThrows(PropertyValidationException.class,
                () -> nodeService.addAspect(folder.getId(), "cm:titled"));
            assertTrue(ex.getMessage().contains("mandatory"));
            assertEquals(1, ex.getViolations().size());
            assertTrue(ex.getViolations().get(0).contains("cm:title"));
            verify(nodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("passes when mandatory property has default value applied")
        void passesMandatoryWithDefault() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>());
            stubWritable(folder);

            PropertyDefinition mandatoryProp = propDef("title", true, "Default Title");
            when(dictionaryService.getPropertiesForAspect("cm:titled")).thenReturn(List.of(mandatoryProp));

            assertDoesNotThrow(() -> nodeService.addAspect(folder.getId(), "cm:titled"));
            assertEquals("Default Title", folder.getProperties().get("cm:title"));
        }

        @Test
        @DisplayName("skips enforcement for unmanaged aspects (no definition)")
        void skipsUnmanagedAspect() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>());
            stubWritable(folder);

            when(dictionaryService.getPropertiesForAspect("custom:unregistered"))
                .thenThrow(new NoSuchElementException("Aspect not found"));

            assertDoesNotThrow(() -> nodeService.addAspect(folder.getId(), "custom:unregistered"));
            assertTrue(folder.hasAspect("custom:unregistered"));
        }
    }

    // ================================================================= constraint enforcement

    @Nested
    @DisplayName("constraint enforcement on addAspect")
    class ConstraintEnforcement {

        @Test
        @DisplayName("rejects property value that fails REGEX constraint")
        void rejectsRegexViolation() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>(Map.of("cm:email", "not-an-email")));
            stubReadWrite(folder);

            PropertyDefinition emailProp = propDef("email", false, null);
            ConstraintDefinition regex = new ConstraintDefinition();
            regex.setConstraintType(ConstraintType.REGEX);
            regex.setParameters(Map.of("expression", "^[\\w.]+@[\\w.]+$"));
            emailProp.setConstraints(List.of(regex));

            when(dictionaryService.getPropertiesForAspect("cm:emailable")).thenReturn(List.of(emailProp));

            PropertyValidationException ex = assertThrows(PropertyValidationException.class,
                () -> nodeService.addAspect(folder.getId(), "cm:emailable"));
            assertTrue(ex.getMessage().contains("match"));
            assertFalse(ex.getViolations().isEmpty());
        }

        @Test
        @DisplayName("rejects property value that fails LIST constraint")
        void rejectsListViolation() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>(Map.of("cm:priority", "URGENT")));
            stubReadWrite(folder);

            PropertyDefinition priorityProp = propDef("priority", false, null);
            ConstraintDefinition list = new ConstraintDefinition();
            list.setConstraintType(ConstraintType.LIST);
            list.setParameters(Map.of("allowedValues", List.of("LOW", "MEDIUM", "HIGH")));
            priorityProp.setConstraints(List.of(list));

            when(dictionaryService.getPropertiesForAspect("cm:classifiable")).thenReturn(List.of(priorityProp));

            PropertyValidationException ex = assertThrows(PropertyValidationException.class,
                () -> nodeService.addAspect(folder.getId(), "cm:classifiable"));
            assertFalse(ex.getViolations().isEmpty());
        }

        @Test
        @DisplayName("passes when property value satisfies all constraints")
        void passesValidConstraints() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>(Map.of("cm:priority", "HIGH")));
            stubWritable(folder);

            PropertyDefinition priorityProp = propDef("priority", false, null);
            ConstraintDefinition list = new ConstraintDefinition();
            list.setConstraintType(ConstraintType.LIST);
            list.setParameters(Map.of("allowedValues", List.of("LOW", "MEDIUM", "HIGH")));
            priorityProp.setConstraints(List.of(list));

            when(dictionaryService.getPropertiesForAspect("cm:classifiable")).thenReturn(List.of(priorityProp));

            assertDoesNotThrow(() -> nodeService.addAspect(folder.getId(), "cm:classifiable"));
        }

        @Test
        @DisplayName("rejects RANGE violation")
        void rejectsRangeViolation() {
            Folder folder = folder("test");
            folder.setProperties(new HashMap<>(Map.of("cm:rating", 200)));
            stubReadWrite(folder);

            PropertyDefinition ratingProp = propDef("rating", false, null);
            ConstraintDefinition range = new ConstraintDefinition();
            range.setConstraintType(ConstraintType.RANGE);
            range.setParameters(Map.of("minValue", 0, "maxValue", 100));
            ratingProp.setConstraints(List.of(range));

            when(dictionaryService.getPropertiesForAspect("cm:rateable")).thenReturn(List.of(ratingProp));

            PropertyValidationException ex = assertThrows(PropertyValidationException.class,
                () -> nodeService.addAspect(folder.getId(), "cm:rateable"));
            assertFalse(ex.getViolations().isEmpty());
        }
    }

    // ================================================================= updateNode enforcement

    @Nested
    @DisplayName("updateNode enforcement")
    class UpdateNodeEnforcement {

        @Test
        @DisplayName("rejects update that violates mandatory property of attached aspect")
        void rejectsMandatoryViolationOnUpdate() {
            Folder folder = folder("test");
            folder.addAspect("cm:titled");
            folder.setProperties(new HashMap<>(Map.of("cm:title", "Hello")));
            when(nodeRepository.findByIdAndDeletedFalse(folder.getId())).thenReturn(Optional.of(folder));
            when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
            when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(true);

            PropertyDefinition mandatoryProp = propDef("title", true, null);
            when(dictionaryService.getPropertiesForAspect("cm:titled")).thenReturn(List.of(mandatoryProp));

            // update: blank out the mandatory property
            Map<String, Object> updates = Map.of("properties", Map.of("cm:title", ""));

            PropertyValidationException ex = assertThrows(PropertyValidationException.class,
                () -> nodeService.updateNode(folder.getId(), new HashMap<>(updates)));
            assertFalse(ex.getViolations().isEmpty());
        }

        @Test
        @DisplayName("passes update when mandatory properties are still present")
        void passesValidUpdate() {
            Folder folder = folder("test");
            folder.addAspect("cm:titled");
            folder.setProperties(new HashMap<>(Map.of("cm:title", "Hello")));
            when(nodeRepository.findByIdAndDeletedFalse(folder.getId())).thenReturn(Optional.of(folder));
            when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
            when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(true);
            when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PropertyDefinition mandatoryProp = propDef("title", true, null);
            when(dictionaryService.getPropertiesForAspect("cm:titled")).thenReturn(List.of(mandatoryProp));

            Map<String, Object> updates = Map.of("properties", Map.of("cm:title", "Updated Title"));

            assertDoesNotThrow(() -> nodeService.updateNode(folder.getId(), new HashMap<>(updates)));
        }
    }

    // ================================================================= createNode enforcement

    @Nested
    @DisplayName("createNode enforcement")
    class CreateNodeEnforcement {

        @Test
        @DisplayName("rejects node creation with aspect that has missing mandatory property")
        void rejectsMandatoryOnCreate() {
            Folder parent = folder("parent");
            when(folderRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
            when(securityService.hasPermission(parent, PermissionType.CREATE_CHILDREN)).thenReturn(true);
            when(nodeRepository.findByParentIdAndName(parent.getId(), "child")).thenReturn(Optional.empty());

            Folder child = new Folder();
            child.setName("child");
            child.setPath("/parent/child");
            child.addAspect("cm:titled");
            child.setProperties(new HashMap<>());

            PropertyDefinition mandatoryProp = propDef("title", true, null);
            when(dictionaryService.getPropertiesForAspect("cm:titled")).thenReturn(List.of(mandatoryProp));

            PropertyValidationException ex = assertThrows(PropertyValidationException.class,
                () -> nodeService.createNode(child, parent.getId()));
            assertFalse(ex.getViolations().isEmpty());
            verify(nodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("passes creation when mandatory property is present")
        void passesWithMandatoryPresent() {
            Folder parent = folder("parent");
            when(folderRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
            when(securityService.hasPermission(parent, PermissionType.CREATE_CHILDREN)).thenReturn(true);
            when(nodeRepository.findByParentIdAndName(parent.getId(), "child")).thenReturn(Optional.empty());
            when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Folder child = new Folder();
            child.setName("child");
            child.setPath("/parent/child");
            child.addAspect("cm:titled");
            child.setProperties(new HashMap<>(Map.of("cm:title", "My Folder")));

            PropertyDefinition mandatoryProp = propDef("title", true, null);
            when(dictionaryService.getPropertiesForAspect("cm:titled")).thenReturn(List.of(mandatoryProp));

            assertDoesNotThrow(() -> nodeService.createNode(child, parent.getId()));
        }
    }

    // ================================================================= helpers

    private Folder folder(String name) {
        Folder f = new Folder();
        f.setId(UUID.randomUUID());
        f.setName(name);
        f.setPath("/" + name);
        return f;
    }

    private void stubWritable(Folder folder) {
        when(nodeRepository.findByIdAndDeletedFalse(folder.getId())).thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(true);
        when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Like stubWritable but without save mock — for tests that throw before save. */
    private void stubReadWrite(Folder folder) {
        when(nodeRepository.findByIdAndDeletedFalse(folder.getId())).thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(true);
    }

    /**
     * Build a PropertyDefinition with a qualified name prefix "cm:" for testing.
     */
    private PropertyDefinition propDef(String name, boolean mandatory, String defaultValue) {
        PropertyDefinition def = new PropertyDefinition();
        def.setId(UUID.randomUUID());
        def.setName(name);
        def.setDataType(PropertyDataType.TEXT);
        def.setMandatory(mandatory);
        def.setDefaultValue(defaultValue);

        // Set up a mock model so qualifiedName() returns "cm:<name>"
        ContentModelDefinition model = new ContentModelDefinition();
        model.setPrefix("cm");
        AspectDefinition aspect = new AspectDefinition();
        aspect.setModel(model);
        def.setAspectDefinition(aspect);

        return def;
    }
}
