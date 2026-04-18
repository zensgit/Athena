package com.ecm.core.service;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.ModelStatus;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.exception.ModelValidationException;
import com.ecm.core.repository.AspectDefinitionRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PropertyDefinitionRepository;
import com.ecm.core.repository.TypeDefinitionRepository;
import com.ecm.core.security.secret.SecretCryptoProperties;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeModelValidationServiceTest {

    @Mock
    private TypeDefinitionRepository typeRepository;

    @Mock
    private AspectDefinitionRepository aspectRepository;

    @Mock
    private PropertyDefinitionRepository propertyRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private SecretCryptoProperties secretCryptoProperties;

    @InjectMocks
    private RuntimeModelValidationService runtimeModelValidationService;

    @Nested
    @DisplayName("activation validation")
    class ActivationValidation {

        @Test
        void rejectsCircularTypeInheritance() {
            ContentModelDefinition model = model("acme", ModelStatus.DRAFT);

            TypeDefinition invoice = type(model, "invoice", "acme:payable");
            TypeDefinition payable = type(model, "payable", "acme:invoice");

            when(typeRepository.findByModelId(model.getId())).thenReturn(List.of(invoice, payable));
            when(aspectRepository.findByModelId(model.getId())).thenReturn(List.of());
            when(propertyRepository.findByTypeDefinitionId(invoice.getId())).thenReturn(List.of());
            when(propertyRepository.findByTypeDefinitionId(payable.getId())).thenReturn(List.of());

            ModelValidationException ex = assertThrows(
                ModelValidationException.class,
                () -> runtimeModelValidationService.validateModelActivation(model)
            );

            org.assertj.core.api.Assertions.assertThat(ex.getViolations())
                .anyMatch(v -> v.contains("Circular type inheritance"));
        }

        @Test
        void rejectsMissingParentAspect() {
            ContentModelDefinition model = model("acme", ModelStatus.DRAFT);
            AspectDefinition aspect = aspect(model, "classifiable", "acme:missingParent");

            when(typeRepository.findByModelId(model.getId())).thenReturn(List.of());
            when(aspectRepository.findByModelId(model.getId())).thenReturn(List.of(aspect));
            when(propertyRepository.findByAspectDefinitionId(aspect.getId())).thenReturn(List.of());
            when(aspectRepository.findByQualifiedName("acme", "missingParent")).thenReturn(Optional.empty());

            ModelValidationException ex = assertThrows(
                ModelValidationException.class,
                () -> runtimeModelValidationService.validateModelActivation(model)
            );

            org.assertj.core.api.Assertions.assertThat(ex.getViolations())
                .contains("Missing parent aspect 'acme:missingParent' for 'acme:classifiable'");
        }

        @Test
        void allowsValidHierarchyReferencingActiveExternalParent() {
            ContentModelDefinition model = model("acme", ModelStatus.DRAFT);
            ContentModelDefinition baseModel = model("cm", ModelStatus.ACTIVE);

            TypeDefinition invoice = type(model, "invoice", "cm:content");
            TypeDefinition parent = type(baseModel, "content", null);

            when(typeRepository.findByModelId(model.getId())).thenReturn(List.of(invoice));
            when(aspectRepository.findByModelId(model.getId())).thenReturn(List.of());
            when(propertyRepository.findByTypeDefinitionId(invoice.getId())).thenReturn(List.of());
            when(typeRepository.findByQualifiedName("cm", "content")).thenReturn(Optional.of(parent));

            assertDoesNotThrow(() -> runtimeModelValidationService.validateModelActivation(model));
        }

        @Test
        void rejectsEncryptedIndexedProperty() {
            ContentModelDefinition model = model("acme", ModelStatus.DRAFT);
            TypeDefinition type = type(model, "invoice", null);
            PropertyDefinition property = property(type, "secretCode");
            property.setEncrypted(true);
            property.setIndexed(true);

            when(typeRepository.findByModelId(model.getId())).thenReturn(List.of(type));
            when(aspectRepository.findByModelId(model.getId())).thenReturn(List.of());
            when(propertyRepository.findByTypeDefinitionId(type.getId())).thenReturn(List.of(property));
            when(secretCryptoProperties.isEnabled()).thenReturn(true);

            ModelValidationException ex = assertThrows(
                ModelValidationException.class,
                () -> runtimeModelValidationService.validateModelActivation(model)
            );

            org.assertj.core.api.Assertions.assertThat(ex.getViolations())
                .contains("Encrypted property 'secretCode' cannot be indexed");
        }
    }

    @Nested
    @DisplayName("deletion validation")
    class DeletionValidation {

        @Test
        void rejectsDeletingTypeThatIsInUse() {
            ContentModelDefinition model = model("acme", ModelStatus.DRAFT);
            TypeDefinition type = type(model, "invoice", null);

            when(nodeRepository.countByTypeQNameAndDeletedFalse("acme:invoice")).thenReturn(2L);

            ModelValidationException ex = assertThrows(
                ModelValidationException.class,
                () -> runtimeModelValidationService.validateTypeDeletion(type)
            );

            org.assertj.core.api.Assertions.assertThat(ex.getViolations())
                .contains("Type 'acme:invoice' is used by 2 live node(s)");
        }

        @Test
        void rejectsDeletingPropertyThatExistsOnLiveNodes() {
            ContentModelDefinition model = model("acme", ModelStatus.DRAFT);
            TypeDefinition type = type(model, "invoice", null);
            PropertyDefinition property = property(type, "invoiceNo");

            when(nodeRepository.countByPropertyKeyAcrossStorageAndDeletedFalse("acme:invoiceNo")).thenReturn(1L);

            ModelValidationException ex = assertThrows(
                ModelValidationException.class,
                () -> runtimeModelValidationService.validatePropertyDeletion(property)
            );

            org.assertj.core.api.Assertions.assertThat(ex.getViolations())
                .contains("Property 'acme:invoiceNo' exists on 1 live node(s)");
        }

        @Test
        void rejectsStructuralMutationOnActiveModel() {
            ContentModelDefinition model = model("acme", ModelStatus.ACTIVE);

            ModelValidationException ex = assertThrows(
                ModelValidationException.class,
                () -> runtimeModelValidationService.ensureStructuralMutationAllowed(model, "type create")
            );

            org.assertj.core.api.Assertions.assertThat(ex.getViolations())
                .contains("type create is not allowed while model 'acme' is ACTIVE");
        }
    }

    private ContentModelDefinition model(String prefix, ModelStatus status) {
        ContentModelDefinition model = new ContentModelDefinition();
        model.setId(UUID.randomUUID());
        model.setPrefix(prefix);
        model.setNamespaceUri("http://example.com/model/" + prefix + "/1.0");
        model.setName(prefix + " model");
        model.setStatus(status);
        return model;
    }

    private TypeDefinition type(ContentModelDefinition model, String name, String parentName) {
        TypeDefinition type = new TypeDefinition();
        type.setId(UUID.randomUUID());
        type.setModel(model);
        type.setName(name);
        type.setParentName(parentName);
        return type;
    }

    private AspectDefinition aspect(ContentModelDefinition model, String name, String parentName) {
        AspectDefinition aspect = new AspectDefinition();
        aspect.setId(UUID.randomUUID());
        aspect.setModel(model);
        aspect.setName(name);
        aspect.setParentName(parentName);
        return aspect;
    }

    private PropertyDefinition property(TypeDefinition type, String name) {
        PropertyDefinition property = new PropertyDefinition();
        property.setId(UUID.randomUUID());
        property.setTypeDefinition(type);
        property.setName(name);
        return property;
    }
}
