package com.ecm.core.service;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.ConstraintDefinition;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.ModelStatus;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.repository.AspectDefinitionRepository;
import com.ecm.core.repository.ConstraintDefinitionRepository;
import com.ecm.core.repository.ContentModelDefinitionRepository;
import com.ecm.core.repository.PropertyDefinitionRepository;
import com.ecm.core.repository.TypeDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentModelServiceTest {

    @Mock
    private ContentModelDefinitionRepository contentModelDefinitionRepository;

    @Mock
    private TypeDefinitionRepository typeDefinitionRepository;

    @Mock
    private AspectDefinitionRepository aspectDefinitionRepository;

    @Mock
    private PropertyDefinitionRepository propertyDefinitionRepository;

    @Mock
    private ConstraintDefinitionRepository constraintDefinitionRepository;

    @Mock
    private RuntimeModelValidationService runtimeModelValidationService;

    @InjectMocks
    private ContentModelService contentModelService;

    @Nested
    class CreateModel {

        @Captor
        private ArgumentCaptor<ContentModelDefinition> modelCaptor;

        @Test
        void createsModelWithValidPrefixAndNamespace() {
            ContentModelDefinition model = new ContentModelDefinition();
            model.setPrefix("custom");
            model.setNamespaceUri("http://example.com/model/custom/1.0");
            model.setName("Custom Model");

            when(contentModelDefinitionRepository.existsByPrefix("custom")).thenReturn(false);
            when(contentModelDefinitionRepository.existsByNamespaceUri("http://example.com/model/custom/1.0"))
                    .thenReturn(false);
            when(contentModelDefinitionRepository.save(any(ContentModelDefinition.class)))
                    .thenAnswer(inv -> {
                        ContentModelDefinition saved = inv.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            ContentModelDefinition result = contentModelService.createModel(model);

            verify(contentModelDefinitionRepository).save(modelCaptor.capture());
            ContentModelDefinition captured = modelCaptor.getValue();
            assertThat(captured.getPrefix()).isEqualTo("custom");
            assertThat(captured.getNamespaceUri()).isEqualTo("http://example.com/model/custom/1.0");
            assertThat(captured.getStatus()).isEqualTo(ModelStatus.DRAFT);
            assertThat(result.getId()).isNotNull();
        }

        @Test
        void rejectsDuplicatePrefix() {
            ContentModelDefinition existing = new ContentModelDefinition();
            existing.setId(UUID.randomUUID());
            existing.setPrefix("custom");

            when(contentModelDefinitionRepository.existsByPrefix("custom")).thenReturn(true);

            ContentModelDefinition model = new ContentModelDefinition();
            model.setPrefix("custom");
            model.setNamespaceUri("http://example.com/model/custom2/1.0");
            model.setName("Another Model");

            assertThatThrownBy(() -> contentModelService.createModel(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("prefix");

            verify(contentModelDefinitionRepository, never()).save(any());
        }

        @Test
        void rejectsDuplicateNamespaceUri() {
            String nsUri = "http://example.com/model/custom/1.0";

            ContentModelDefinition existing = new ContentModelDefinition();
            existing.setId(UUID.randomUUID());
            existing.setNamespaceUri(nsUri);

            when(contentModelDefinitionRepository.existsByPrefix("newprefix")).thenReturn(false);
            when(contentModelDefinitionRepository.existsByNamespaceUri(nsUri)).thenReturn(true);

            ContentModelDefinition model = new ContentModelDefinition();
            model.setPrefix("newprefix");
            model.setNamespaceUri(nsUri);
            model.setName("Duplicate NS Model");

            assertThatThrownBy(() -> contentModelService.createModel(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("namespace");

            verify(contentModelDefinitionRepository, never()).save(any());
        }
    }

    @Nested
    class Lifecycle {

        private UUID modelId;
        private ContentModelDefinition model;

        @BeforeEach
        void setUp() {
            modelId = UUID.randomUUID();
            model = new ContentModelDefinition();
            model.setId(modelId);
            model.setPrefix("cm");
            model.setNamespaceUri("http://example.com/model/cm/1.0");
            model.setName("Content Model");
        }

        @Test
        void activatesADraftModel() {
            model.setStatus(ModelStatus.DRAFT);

            when(contentModelDefinitionRepository.findById(modelId)).thenReturn(Optional.of(model));
            when(contentModelDefinitionRepository.save(any(ContentModelDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ContentModelDefinition result = contentModelService.activateModel(modelId);

            assertThat(result.getStatus()).isEqualTo(ModelStatus.ACTIVE);
            verify(contentModelDefinitionRepository).save(model);
        }

        @Test
        void deactivatesAnActiveModel() {
            model.setStatus(ModelStatus.ACTIVE);

            when(contentModelDefinitionRepository.findById(modelId)).thenReturn(Optional.of(model));
            when(contentModelDefinitionRepository.save(any(ContentModelDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ContentModelDefinition result = contentModelService.deactivateModel(modelId);

            assertThat(result.getStatus()).isEqualTo(ModelStatus.DISABLED);
            verify(contentModelDefinitionRepository).save(model);
        }

        @Test
        void rejectsDeletionOfActiveModel() {
            model.setStatus(ModelStatus.ACTIVE);

            when(contentModelDefinitionRepository.findById(modelId)).thenReturn(Optional.of(model));

            assertThatThrownBy(() -> contentModelService.deleteModel(modelId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("deactivate");

            verify(contentModelDefinitionRepository, never()).delete(any());
        }
    }

    @Nested
    class AddType {

        @Captor
        private ArgumentCaptor<TypeDefinition> typeCaptor;

        @Test
        void addsTypeToModel() {
            UUID modelId = UUID.randomUUID();
            ContentModelDefinition model = new ContentModelDefinition();
            model.setId(modelId);
            model.setPrefix("custom");
            model.setName("Custom Model");
            model.setStatus(ModelStatus.DRAFT);

            when(contentModelDefinitionRepository.findById(modelId)).thenReturn(Optional.of(model));
            when(typeDefinitionRepository.save(any(TypeDefinition.class)))
                    .thenAnswer(inv -> {
                        TypeDefinition saved = inv.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            TypeDefinition typeDef = new TypeDefinition();
            typeDef.setName("custom:document");

            TypeDefinition result = contentModelService.addType(modelId, typeDef);

            verify(typeDefinitionRepository).save(typeCaptor.capture());
            TypeDefinition captured = typeCaptor.getValue();
            assertThat(captured.getName()).isEqualTo("custom:document");
            assertThat(captured.getModel()).isEqualTo(model);
            assertThat(result.getId()).isNotNull();
        }

        @Test
        void rejectsAddingTypeToNonExistentModel() {
            UUID modelId = UUID.randomUUID();

            when(contentModelDefinitionRepository.findById(modelId)).thenReturn(Optional.empty());

            TypeDefinition typeDef = new TypeDefinition();
            typeDef.setName("custom:document");

            assertThatThrownBy(() -> contentModelService.addType(modelId, typeDef))
                    .isInstanceOf(NoSuchElementException.class);

            verify(typeDefinitionRepository, never()).save(any());
        }
    }

    @Nested
    class AddAspectDefinitionTest {

        @Captor
        private ArgumentCaptor<AspectDefinition> aspectCaptor;

        @Test
        void addsAspectToModel() {
            UUID modelId = UUID.randomUUID();
            ContentModelDefinition model = new ContentModelDefinition();
            model.setId(modelId);
            model.setPrefix("custom");
            model.setName("Custom Model");
            model.setStatus(ModelStatus.DRAFT);

            when(contentModelDefinitionRepository.findById(modelId)).thenReturn(Optional.of(model));
            when(aspectDefinitionRepository.save(any(AspectDefinition.class)))
                    .thenAnswer(inv -> {
                        AspectDefinition saved = inv.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            AspectDefinition aspectDef = new AspectDefinition();
            aspectDef.setName("custom:auditable");

            AspectDefinition result = contentModelService.addAspectDefinition(modelId, aspectDef);

            verify(aspectDefinitionRepository).save(aspectCaptor.capture());
            AspectDefinition captured = aspectCaptor.getValue();
            assertThat(captured.getName()).isEqualTo("custom:auditable");
            assertThat(captured.getModel()).isEqualTo(model);
            assertThat(result.getId()).isNotNull();
        }
    }

    @Nested
    class AddProperty {

        @Captor
        private ArgumentCaptor<PropertyDefinition> propertyCaptor;

        @Test
        void addsPropertyToTypeDefinition() {
            UUID typeId = UUID.randomUUID();
            TypeDefinition typeDef = new TypeDefinition();
            typeDef.setId(typeId);
            typeDef.setName("custom:document");

            when(typeDefinitionRepository.findById(typeId)).thenReturn(Optional.of(typeDef));
            when(propertyDefinitionRepository.save(any(PropertyDefinition.class)))
                    .thenAnswer(inv -> {
                        PropertyDefinition saved = inv.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            PropertyDefinition propDef = new PropertyDefinition();
            propDef.setName("custom:title");

            PropertyDefinition result = contentModelService.addProperty(typeId, propDef, false);

            verify(runtimeModelValidationService).validatePropertyDefinition(propDef);
            verify(propertyDefinitionRepository).save(propertyCaptor.capture());
            PropertyDefinition captured = propertyCaptor.getValue();
            assertThat(captured.getName()).isEqualTo("custom:title");
            assertThat(captured.getTypeDefinition()).isEqualTo(typeDef);
            assertThat(result.getId()).isNotNull();
        }

        @Test
        void addsPropertyToAspectDefinition() {
            UUID aspectId = UUID.randomUUID();
            AspectDefinition aspectDef = new AspectDefinition();
            aspectDef.setId(aspectId);
            aspectDef.setName("custom:auditable");

            when(aspectDefinitionRepository.findById(aspectId)).thenReturn(Optional.of(aspectDef));
            when(propertyDefinitionRepository.save(any(PropertyDefinition.class)))
                    .thenAnswer(inv -> {
                        PropertyDefinition saved = inv.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            PropertyDefinition propDef = new PropertyDefinition();
            propDef.setName("custom:createdDate");

            PropertyDefinition result = contentModelService.addProperty(aspectId, propDef, true);

            verify(runtimeModelValidationService).validatePropertyDefinition(propDef);
            verify(propertyDefinitionRepository).save(propertyCaptor.capture());
            PropertyDefinition captured = propertyCaptor.getValue();
            assertThat(captured.getName()).isEqualTo("custom:createdDate");
            assertThat(captured.getAspectDefinition()).isEqualTo(aspectDef);
            assertThat(result.getId()).isNotNull();
        }

        @Test
        void rejectsInvalidEncryptedPropertyDefinitionBeforeLookup() {
            UUID typeId = UUID.randomUUID();
            PropertyDefinition propDef = new PropertyDefinition();
            propDef.setName("custom:secret");
            propDef.setEncrypted(true);

            RuntimeException failure = new RuntimeException("Encrypted property invalid");
            org.mockito.Mockito.doThrow(failure)
                .when(runtimeModelValidationService)
                .validatePropertyDefinition(propDef);

            assertThatThrownBy(() -> contentModelService.addProperty(typeId, propDef, false))
                .isSameAs(failure);

            verify(runtimeModelValidationService).validatePropertyDefinition(propDef);
            verifyNoInteractions(typeDefinitionRepository, aspectDefinitionRepository, propertyDefinitionRepository);
        }

        @Test
        void coercesEncryptedPropertyToNonIndexedBeforeValidation() {
            UUID typeId = UUID.randomUUID();
            TypeDefinition typeDef = new TypeDefinition();
            typeDef.setId(typeId);
            typeDef.setName("custom:document");

            when(typeDefinitionRepository.findById(typeId)).thenReturn(Optional.of(typeDef));
            when(propertyDefinitionRepository.save(any(PropertyDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            PropertyDefinition propDef = new PropertyDefinition();
            propDef.setName("custom:secret");
            propDef.setEncrypted(true);
            propDef.setIndexed(true);

            contentModelService.addProperty(typeId, propDef, false);

            verify(runtimeModelValidationService).validatePropertyDefinition(propDef);
            verify(propertyDefinitionRepository).save(propertyCaptor.capture());
            assertThat(propDef.isIndexed()).isFalse();
            assertThat(propertyCaptor.getValue().isIndexed()).isFalse();
        }
    }

    @Nested
    class AddConstraint {

        @Captor
        private ArgumentCaptor<ConstraintDefinition> constraintCaptor;

        @Test
        void addsConstraintToProperty() {
            UUID propertyId = UUID.randomUUID();
            PropertyDefinition propDef = new PropertyDefinition();
            propDef.setId(propertyId);
            propDef.setName("custom:status");

            when(propertyDefinitionRepository.findById(propertyId)).thenReturn(Optional.of(propDef));
            when(constraintDefinitionRepository.save(any(ConstraintDefinition.class)))
                    .thenAnswer(inv -> {
                        ConstraintDefinition saved = inv.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            ConstraintDefinition constraintDef = new ConstraintDefinition();
            constraintDef.setConstraintType(com.ecm.core.entity.ConstraintType.LIST);

            ConstraintDefinition result = contentModelService.addConstraint(propertyId, constraintDef);

            verify(constraintDefinitionRepository).save(constraintCaptor.capture());
            ConstraintDefinition captured = constraintCaptor.getValue();
            assertThat(captured.getConstraintType()).isEqualTo(com.ecm.core.entity.ConstraintType.LIST);
            assertThat(captured.getPropertyDefinition()).isEqualTo(propDef);
            assertThat(result.getId()).isNotNull();
        }
    }
}
