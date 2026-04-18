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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RuntimeModelValidationService {

    private final TypeDefinitionRepository typeRepository;
    private final AspectDefinitionRepository aspectRepository;
    private final PropertyDefinitionRepository propertyRepository;
    private final NodeRepository nodeRepository;
    private final com.ecm.core.security.secret.SecretCryptoProperties secretCryptoProperties;

    public void ensureStructuralMutationAllowed(ContentModelDefinition model, String operation) {
        if (model != null && model.getStatus() == ModelStatus.ACTIVE) {
            throw validationFailure(
                "Active models cannot be structurally modified",
                operation + " is not allowed while model '" + model.getPrefix() + "' is ACTIVE"
            );
        }
    }

    public void validateModelActivation(ContentModelDefinition model) {
        List<String> violations = new ArrayList<>();

        validateTypeHierarchy(typeRepository.findByModelId(model.getId()), violations);
        validateAspectHierarchy(aspectRepository.findByModelId(model.getId()), violations);
        for (PropertyDefinition property : getModelProperties(model.getId())) {
            appendPropertyDefinitionViolations(property, violations);
        }

        if (!violations.isEmpty()) {
            throw validationFailure("Model validation failed", violations);
        }
    }

    public void validateModelDeletion(ContentModelDefinition model) {
        List<String> violations = new ArrayList<>();

        for (TypeDefinition type : typeRepository.findByModelId(model.getId())) {
            appendTypeUsageViolation(type, violations);
        }
        for (AspectDefinition aspect : aspectRepository.findByModelId(model.getId())) {
            appendAspectUsageViolation(aspect, violations);
        }
        for (PropertyDefinition property : getModelProperties(model.getId())) {
            appendPropertyUsageViolation(property, violations);
        }

        if (!violations.isEmpty()) {
            throw validationFailure("Cannot delete model while definitions are in use", violations);
        }
    }

    public void validateTypeUpdate(TypeDefinition type, String proposedParentName) {
        if (proposedParentName == null) {
            return;
        }
        ensureStructuralMutationAllowed(type.getModel(), "type inheritance update");
        List<String> violations = new ArrayList<>();
        validateTypeHierarchy(typeRepository.findByModelId(type.getModel().getId()), type.getId(), proposedParentName, violations);
        if (!violations.isEmpty()) {
            throw validationFailure("Type validation failed", violations);
        }
    }

    public void validateAspectUpdate(AspectDefinition aspect, String proposedParentName) {
        if (proposedParentName == null) {
            return;
        }
        ensureStructuralMutationAllowed(aspect.getModel(), "aspect inheritance update");
        List<String> violations = new ArrayList<>();
        validateAspectHierarchy(aspectRepository.findByModelId(aspect.getModel().getId()), aspect.getId(), proposedParentName, violations);
        if (!violations.isEmpty()) {
            throw validationFailure("Aspect validation failed", violations);
        }
    }

    public void validateTypeDeletion(TypeDefinition type) {
        ensureStructuralMutationAllowed(type.getModel(), "type delete");
        List<String> violations = new ArrayList<>();
        appendTypeUsageViolation(type, violations);
        if (!violations.isEmpty()) {
            throw validationFailure("Cannot delete type while it is in use", violations);
        }
    }

    public void validateAspectDeletion(AspectDefinition aspect) {
        ensureStructuralMutationAllowed(aspect.getModel(), "aspect delete");
        List<String> violations = new ArrayList<>();
        appendAspectUsageViolation(aspect, violations);
        if (!violations.isEmpty()) {
            throw validationFailure("Cannot delete aspect while it is in use", violations);
        }
    }

    public void validatePropertyDeletion(PropertyDefinition property) {
        ContentModelDefinition model = property.getTypeDefinition() != null
            ? property.getTypeDefinition().getModel()
            : property.getAspectDefinition().getModel();
        ensureStructuralMutationAllowed(model, "property delete");
        List<String> violations = new ArrayList<>();
        appendPropertyUsageViolation(property, violations);
        if (!violations.isEmpty()) {
            throw validationFailure("Cannot delete property while it is in use", violations);
        }
    }

    public void validateConstraintDeletion(PropertyDefinition property) {
        if (property == null) {
            return;
        }
        ContentModelDefinition model = property.getTypeDefinition() != null
            ? property.getTypeDefinition().getModel()
            : property.getAspectDefinition().getModel();
        ensureStructuralMutationAllowed(model, "constraint delete");
    }

    public void validatePropertyDefinition(PropertyDefinition property) {
        List<String> violations = new ArrayList<>();
        appendPropertyDefinitionViolations(property, violations);
        if (!violations.isEmpty()) {
            throw validationFailure("Property validation failed", violations);
        }
    }

    private void appendTypeUsageViolation(TypeDefinition type, List<String> violations) {
        long usageCount = nodeRepository.countByTypeQNameAndDeletedFalse(type.qualifiedName());
        if (usageCount > 0) {
            violations.add("Type '" + type.qualifiedName() + "' is used by " + usageCount + " live node(s)");
        }
    }

    private void appendAspectUsageViolation(AspectDefinition aspect, List<String> violations) {
        long usageCount = nodeRepository.countByAspectNameAndDeletedFalse(aspect.qualifiedName());
        if (usageCount > 0) {
            violations.add("Aspect '" + aspect.qualifiedName() + "' is used by " + usageCount + " live node(s)");
        }
    }

    private void appendPropertyUsageViolation(PropertyDefinition property, List<String> violations) {
        long usageCount = nodeRepository.countByPropertyKeyAcrossStorageAndDeletedFalse(property.qualifiedName());
        if (usageCount > 0) {
            violations.add("Property '" + property.qualifiedName() + "' exists on " + usageCount + " live node(s)");
        }
    }

    private void appendPropertyDefinitionViolations(PropertyDefinition property, List<String> violations) {
        if (property == null) {
            return;
        }
        if (property.isEncrypted() && property.isIndexed()) {
            violations.add("Encrypted property '" + property.getName() + "' cannot be indexed");
        }
        if (property.isEncrypted() && !secretCryptoProperties.isEnabled()) {
            violations.add("Encrypted property '" + property.getName() + "' requires ecm.security.secret.enabled=true");
        }
    }

    private List<PropertyDefinition> getModelProperties(UUID modelId) {
        List<PropertyDefinition> properties = new ArrayList<>();
        for (TypeDefinition type : typeRepository.findByModelId(modelId)) {
            properties.addAll(propertyRepository.findByTypeDefinitionId(type.getId()));
        }
        for (AspectDefinition aspect : aspectRepository.findByModelId(modelId)) {
            properties.addAll(propertyRepository.findByAspectDefinitionId(aspect.getId()));
        }
        return properties;
    }

    private void validateTypeHierarchy(List<TypeDefinition> types, List<String> violations) {
        validateTypeHierarchy(types, null, null, violations);
    }

    private void validateTypeHierarchy(
        List<TypeDefinition> types,
        UUID overrideId,
        String overrideParentName,
        List<String> violations
    ) {
        Map<String, TypeDefinition> localTypes = new HashMap<>();
        Map<UUID, String> parentOverrides = new HashMap<>();
        for (TypeDefinition type : types) {
            localTypes.put(type.qualifiedName(), type);
            if (overrideId != null && overrideId.equals(type.getId())) {
                parentOverrides.put(type.getId(), blankToNull(overrideParentName));
            }
        }

        Set<String> visited = new HashSet<>();
        for (TypeDefinition type : types) {
            detectTypeCycle(type, localTypes, parentOverrides, new HashSet<>(), visited, violations);
        }
    }

    private void detectTypeCycle(
        TypeDefinition type,
        Map<String, TypeDefinition> localTypes,
        Map<UUID, String> parentOverrides,
        Set<String> visiting,
        Set<String> visited,
        List<String> violations
    ) {
        String currentQName = type.qualifiedName();
        if (visited.contains(currentQName)) {
            return;
        }
        if (!visiting.add(currentQName)) {
            violations.add("Circular type inheritance detected at '" + currentQName + "'");
            return;
        }

        String parentName = parentOverrides.getOrDefault(type.getId(), blankToNull(type.getParentName()));
        if (parentName != null) {
            String normalizedParent = normalizeQualifiedName(type.getModel(), parentName);
            if (currentQName.equals(normalizedParent)) {
                violations.add("Type '" + currentQName + "' cannot inherit from itself");
            } else {
                TypeDefinition parent = localTypes.get(normalizedParent);
                if (parent != null) {
                    detectTypeCycle(parent, localTypes, parentOverrides, visiting, visited, violations);
                } else if (!activeTypeExists(normalizedParent)) {
                    violations.add("Missing parent type '" + normalizedParent + "' for '" + currentQName + "'");
                }
            }
        }

        visiting.remove(currentQName);
        visited.add(currentQName);
    }

    private void validateAspectHierarchy(List<AspectDefinition> aspects, List<String> violations) {
        validateAspectHierarchy(aspects, null, null, violations);
    }

    private void validateAspectHierarchy(
        List<AspectDefinition> aspects,
        UUID overrideId,
        String overrideParentName,
        List<String> violations
    ) {
        Map<String, AspectDefinition> localAspects = new HashMap<>();
        Map<UUID, String> parentOverrides = new HashMap<>();
        for (AspectDefinition aspect : aspects) {
            localAspects.put(aspect.qualifiedName(), aspect);
            if (overrideId != null && overrideId.equals(aspect.getId())) {
                parentOverrides.put(aspect.getId(), blankToNull(overrideParentName));
            }
        }

        Set<String> visited = new HashSet<>();
        for (AspectDefinition aspect : aspects) {
            detectAspectCycle(aspect, localAspects, parentOverrides, new HashSet<>(), visited, violations);
        }
    }

    private void detectAspectCycle(
        AspectDefinition aspect,
        Map<String, AspectDefinition> localAspects,
        Map<UUID, String> parentOverrides,
        Set<String> visiting,
        Set<String> visited,
        List<String> violations
    ) {
        String currentQName = aspect.qualifiedName();
        if (visited.contains(currentQName)) {
            return;
        }
        if (!visiting.add(currentQName)) {
            violations.add("Circular aspect inheritance detected at '" + currentQName + "'");
            return;
        }

        String parentName = parentOverrides.getOrDefault(aspect.getId(), blankToNull(aspect.getParentName()));
        if (parentName != null) {
            String normalizedParent = normalizeQualifiedName(aspect.getModel(), parentName);
            if (currentQName.equals(normalizedParent)) {
                violations.add("Aspect '" + currentQName + "' cannot inherit from itself");
            } else {
                AspectDefinition parent = localAspects.get(normalizedParent);
                if (parent != null) {
                    detectAspectCycle(parent, localAspects, parentOverrides, visiting, visited, violations);
                } else if (!activeAspectExists(normalizedParent)) {
                    violations.add("Missing parent aspect '" + normalizedParent + "' for '" + currentQName + "'");
                }
            }
        }

        visiting.remove(currentQName);
        visited.add(currentQName);
    }

    private boolean activeTypeExists(String qualifiedName) {
        String[] parts = splitQualifiedName(qualifiedName);
        return typeRepository.findByQualifiedName(parts[0], parts[1])
            .filter(type -> type.getModel() != null && type.getModel().getStatus() == ModelStatus.ACTIVE)
            .isPresent();
    }

    private boolean activeAspectExists(String qualifiedName) {
        String[] parts = splitQualifiedName(qualifiedName);
        return aspectRepository.findByQualifiedName(parts[0], parts[1])
            .filter(aspect -> aspect.getModel() != null && aspect.getModel().getStatus() == ModelStatus.ACTIVE)
            .isPresent();
    }

    private String normalizeQualifiedName(ContentModelDefinition model, String name) {
        String normalized = blankToNull(name);
        if (normalized == null) {
            return null;
        }
        return normalized.contains(":") ? normalized : model.getPrefix() + ":" + normalized;
    }

    private String[] splitQualifiedName(String qualifiedName) {
        int separator = qualifiedName.indexOf(':');
        if (separator <= 0 || separator >= qualifiedName.length() - 1) {
            throw validationFailure("Model validation failed", "Invalid qualified name '" + qualifiedName + "'");
        }
        return new String[]{qualifiedName.substring(0, separator), qualifiedName.substring(separator + 1)};
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private ModelValidationException validationFailure(String message, String violation) {
        return validationFailure(message, List.of(violation));
    }

    private ModelValidationException validationFailure(String message, List<String> violations) {
        return new ModelValidationException(message, violations);
    }
}
