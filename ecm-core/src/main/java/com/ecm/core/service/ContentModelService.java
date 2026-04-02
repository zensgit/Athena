package com.ecm.core.service;

import com.ecm.core.entity.*;
import com.ecm.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ContentModelService {

    private final ContentModelDefinitionRepository modelRepo;
    private final TypeDefinitionRepository typeRepo;
    private final AspectDefinitionRepository aspectRepo;
    private final PropertyDefinitionRepository propertyRepo;
    private final ConstraintDefinitionRepository constraintRepo;

    // ------------------------------------------------------------------ model CRUD

    public ContentModelDefinition createModel(ContentModelDefinition model) {
        if (modelRepo.existsByPrefix(model.getPrefix())) {
            throw new IllegalArgumentException("Duplicate prefix: " + model.getPrefix());
        }
        if (modelRepo.existsByNamespaceUri(model.getNamespaceUri())) {
            throw new IllegalArgumentException("Duplicate namespace: " + model.getNamespaceUri());
        }
        model.setStatus(ModelStatus.DRAFT);
        return modelRepo.save(model);
    }

    public ContentModelDefinition updateModel(UUID modelId, String name, String description) {
        ContentModelDefinition model = getModel(modelId);
        model.setName(name);
        model.setDescription(description);
        return modelRepo.save(model);
    }

    public ContentModelDefinition activateModel(UUID modelId) {
        ContentModelDefinition model = getModel(modelId);
        if (model.getStatus() == ModelStatus.ACTIVE) {
            throw new IllegalStateException("Model is already active");
        }
        model.setStatus(ModelStatus.ACTIVE);
        return modelRepo.save(model);
    }

    public ContentModelDefinition deactivateModel(UUID modelId) {
        ContentModelDefinition model = getModel(modelId);
        if (model.getStatus() == ModelStatus.DISABLED) {
            throw new IllegalStateException("Model is already disabled");
        }
        model.setStatus(ModelStatus.DISABLED);
        return modelRepo.save(model);
    }

    public void deleteModel(UUID modelId) {
        ContentModelDefinition model = getModel(modelId);
        if (model.getStatus() == ModelStatus.ACTIVE) {
            throw new IllegalStateException("Cannot delete an active model; deactivate first");
        }
        model.setDeleted(true);
        modelRepo.save(model);
    }

    @Transactional(readOnly = true)
    public ContentModelDefinition getModel(UUID modelId) {
        return modelRepo.findById(modelId)
            .filter(m -> !m.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Content model not found: " + modelId));
    }

    @Transactional(readOnly = true)
    public ContentModelDefinition getModelByPrefix(String prefix) {
        return modelRepo.findByPrefix(prefix)
            .filter(m -> !m.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Model not found for prefix: " + prefix));
    }

    @Transactional(readOnly = true)
    public List<ContentModelDefinition> listModels() {
        return modelRepo.findByDeletedFalse();
    }

    @Transactional(readOnly = true)
    public List<ContentModelDefinition> listActiveModels() {
        return modelRepo.findByStatus(ModelStatus.ACTIVE);
    }

    // ------------------------------------------------------------------ types

    public TypeDefinition addType(UUID modelId, TypeDefinition type) {
        ContentModelDefinition model = getModel(modelId);
        // duplicate-name guard
        boolean dup = typeRepo.findByModelId(modelId).stream()
            .anyMatch(t -> t.getName().equals(type.getName()));
        if (dup) {
            throw new IllegalArgumentException("Type '" + type.getName() + "' already exists in model " + model.getPrefix());
        }
        type.setModel(model);
        return typeRepo.save(type);
    }

    public TypeDefinition updateType(UUID typeId, String title, String description, String parentName) {
        TypeDefinition type = typeRepo.findById(typeId)
            .orElseThrow(() -> new NoSuchElementException("Type not found: " + typeId));
        if (title != null) type.setTitle(title);
        if (description != null) type.setDescription(description);
        if (parentName != null) type.setParentName(parentName.isBlank() ? null : parentName);
        return typeRepo.save(type);
    }

    public void deleteType(UUID typeId) {
        TypeDefinition type = typeRepo.findById(typeId)
            .orElseThrow(() -> new NoSuchElementException("Type not found: " + typeId));
        typeRepo.delete(type);
    }

    // ------------------------------------------------------------------ aspects

    public AspectDefinition addAspectDefinition(UUID modelId, AspectDefinition aspect) {
        ContentModelDefinition model = getModel(modelId);
        boolean dup = aspectRepo.findByModelId(modelId).stream()
            .anyMatch(a -> a.getName().equals(aspect.getName()));
        if (dup) {
            throw new IllegalArgumentException("Aspect '" + aspect.getName() + "' already exists in model " + model.getPrefix());
        }
        aspect.setModel(model);
        return aspectRepo.save(aspect);
    }

    public AspectDefinition updateAspect(UUID aspectId, String title, String description, String parentName) {
        AspectDefinition aspect = aspectRepo.findById(aspectId)
            .orElseThrow(() -> new NoSuchElementException("Aspect not found: " + aspectId));
        if (title != null) aspect.setTitle(title);
        if (description != null) aspect.setDescription(description);
        if (parentName != null) aspect.setParentName(parentName.isBlank() ? null : parentName);
        return aspectRepo.save(aspect);
    }

    public void deleteAspect(UUID aspectId) {
        AspectDefinition aspect = aspectRepo.findById(aspectId)
            .orElseThrow(() -> new NoSuchElementException("Aspect not found: " + aspectId));
        aspectRepo.delete(aspect);
    }

    // ------------------------------------------------------------------ properties

    public PropertyDefinition addProperty(UUID ownerId, PropertyDefinition property, boolean isAspect) {
        if (isAspect) {
            AspectDefinition aspect = aspectRepo.findById(ownerId)
                .orElseThrow(() -> new NoSuchElementException("Aspect not found: " + ownerId));
            boolean dup = propertyRepo.findByAspectDefinitionId(ownerId).stream()
                .anyMatch(p -> p.getName().equals(property.getName()));
            if (dup) {
                throw new IllegalArgumentException("Property '" + property.getName() + "' already exists on aspect");
            }
            property.setAspectDefinition(aspect);
        } else {
            TypeDefinition type = typeRepo.findById(ownerId)
                .orElseThrow(() -> new NoSuchElementException("Type not found: " + ownerId));
            boolean dup = propertyRepo.findByTypeDefinitionId(ownerId).stream()
                .anyMatch(p -> p.getName().equals(property.getName()));
            if (dup) {
                throw new IllegalArgumentException("Property '" + property.getName() + "' already exists on type");
            }
            property.setTypeDefinition(type);
        }
        return propertyRepo.save(property);
    }

    public void deleteProperty(UUID propertyId) {
        PropertyDefinition property = propertyRepo.findById(propertyId)
            .orElseThrow(() -> new NoSuchElementException("Property not found: " + propertyId));
        propertyRepo.delete(property);
    }

    // ------------------------------------------------------------------ constraints

    public ConstraintDefinition addConstraint(UUID propertyId, ConstraintDefinition constraint) {
        PropertyDefinition property = propertyRepo.findById(propertyId)
            .orElseThrow(() -> new NoSuchElementException("Property not found: " + propertyId));
        constraint.setPropertyDefinition(property);
        return constraintRepo.save(constraint);
    }

    public void deleteConstraint(UUID constraintId) {
        ConstraintDefinition constraint = constraintRepo.findById(constraintId)
            .orElseThrow(() -> new NoSuchElementException("Constraint not found: " + constraintId));
        constraintRepo.delete(constraint);
    }
}
