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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentModelValidationTest {

    @Mock private ContentModelDefinitionRepository modelRepo;
    @Mock private TypeDefinitionRepository typeRepo;
    @Mock private AspectDefinitionRepository aspectRepo;
    @Mock private PropertyDefinitionRepository propertyRepo;
    @Mock private ConstraintDefinitionRepository constraintRepo;
    @InjectMocks private ContentModelService service;

    @Nested
    @DisplayName("duplicate type name guard")
    class DuplicateType {

        @Test
        void rejectsDuplicateTypeNameWithinModel() {
            UUID modelId = UUID.randomUUID();
            ContentModelDefinition model = model(modelId, "cm");

            TypeDefinition existing = new TypeDefinition();
            existing.setName("content");
            existing.setModel(model);

            when(modelRepo.findById(modelId)).thenReturn(Optional.of(model));
            when(typeRepo.findByModelId(modelId)).thenReturn(List.of(existing));

            TypeDefinition dup = new TypeDefinition();
            dup.setName("content");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addType(modelId, dup));
            assertTrue(ex.getMessage().contains("already exists"));
            verify(typeRepo, never()).save(any());
        }

        @Test
        void allowsDifferentTypeNames() {
            UUID modelId = UUID.randomUUID();
            ContentModelDefinition model = model(modelId, "cm");

            when(modelRepo.findById(modelId)).thenReturn(Optional.of(model));
            when(typeRepo.findByModelId(modelId)).thenReturn(List.of());
            when(typeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TypeDefinition type = new TypeDefinition();
            type.setName("folder");

            assertDoesNotThrow(() -> service.addType(modelId, type));
            verify(typeRepo).save(any());
        }
    }

    @Nested
    @DisplayName("duplicate aspect name guard")
    class DuplicateAspect {

        @Test
        void rejectsDuplicateAspectNameWithinModel() {
            UUID modelId = UUID.randomUUID();
            ContentModelDefinition model = model(modelId, "cm");

            AspectDefinition existing = new AspectDefinition();
            existing.setName("titled");
            existing.setModel(model);

            when(modelRepo.findById(modelId)).thenReturn(Optional.of(model));
            when(aspectRepo.findByModelId(modelId)).thenReturn(List.of(existing));

            AspectDefinition dup = new AspectDefinition();
            dup.setName("titled");

            assertThrows(IllegalArgumentException.class,
                () -> service.addAspectDefinition(modelId, dup));
        }
    }

    @Nested
    @DisplayName("duplicate property name guard")
    class DuplicateProperty {

        @Test
        void rejectsDuplicatePropertyOnType() {
            UUID typeId = UUID.randomUUID();
            TypeDefinition type = new TypeDefinition();
            type.setId(typeId);
            type.setName("content");

            PropertyDefinition existing = new PropertyDefinition();
            existing.setName("title");

            when(typeRepo.findById(typeId)).thenReturn(Optional.of(type));
            when(propertyRepo.findByTypeDefinitionId(typeId)).thenReturn(List.of(existing));

            PropertyDefinition dup = new PropertyDefinition();
            dup.setName("title");

            assertThrows(IllegalArgumentException.class,
                () -> service.addProperty(typeId, dup, false));
        }

        @Test
        void rejectsDuplicatePropertyOnAspect() {
            UUID aspectId = UUID.randomUUID();
            AspectDefinition aspect = new AspectDefinition();
            aspect.setId(aspectId);
            aspect.setName("titled");

            PropertyDefinition existing = new PropertyDefinition();
            existing.setName("title");

            when(aspectRepo.findById(aspectId)).thenReturn(Optional.of(aspect));
            when(propertyRepo.findByAspectDefinitionId(aspectId)).thenReturn(List.of(existing));

            PropertyDefinition dup = new PropertyDefinition();
            dup.setName("title");

            assertThrows(IllegalArgumentException.class,
                () -> service.addProperty(aspectId, dup, true));
        }
    }

    @Nested
    @DisplayName("update and delete operations")
    class UpdateDelete {

        @Test
        void updateTypeChangesFields() {
            UUID typeId = UUID.randomUUID();
            TypeDefinition type = new TypeDefinition();
            type.setId(typeId);
            type.setName("content");

            when(typeRepo.findById(typeId)).thenReturn(Optional.of(type));
            when(typeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TypeDefinition result = service.updateType(typeId, "My Content", "Custom content type", "cm:base");

            assertEquals("My Content", result.getTitle());
            assertEquals("Custom content type", result.getDescription());
            assertEquals("cm:base", result.getParentName());
        }

        @Test
        void deleteTypeCallsRepo() {
            UUID typeId = UUID.randomUUID();
            TypeDefinition type = new TypeDefinition();
            type.setId(typeId);

            when(typeRepo.findById(typeId)).thenReturn(Optional.of(type));

            service.deleteType(typeId);
            verify(typeRepo).delete(type);
        }

        @Test
        void deletePropertyCallsRepo() {
            UUID propId = UUID.randomUUID();
            PropertyDefinition prop = new PropertyDefinition();
            prop.setId(propId);

            when(propertyRepo.findById(propId)).thenReturn(Optional.of(prop));

            service.deleteProperty(propId);
            verify(propertyRepo).delete(prop);
        }

        @Test
        void deleteConstraintCallsRepo() {
            UUID cId = UUID.randomUUID();
            ConstraintDefinition c = new ConstraintDefinition();
            c.setId(cId);

            when(constraintRepo.findById(cId)).thenReturn(Optional.of(c));

            service.deleteConstraint(cId);
            verify(constraintRepo).delete(c);
        }
    }

    private ContentModelDefinition model(UUID id, String prefix) {
        ContentModelDefinition m = new ContentModelDefinition();
        m.setId(id);
        m.setPrefix(prefix);
        m.setName("test");
        m.setNamespaceUri("http://test.com/" + prefix);
        m.setStatus(ModelStatus.DRAFT);
        return m;
    }
}
