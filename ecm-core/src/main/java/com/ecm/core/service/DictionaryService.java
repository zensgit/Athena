package com.ecm.core.service;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.repository.AspectDefinitionRepository;
import com.ecm.core.repository.ContentModelDefinitionRepository;
import com.ecm.core.repository.PropertyDefinitionRepository;
import com.ecm.core.repository.TypeDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class DictionaryService {

    private final TypeDefinitionRepository typeDefinitionRepository;
    private final AspectDefinitionRepository aspectDefinitionRepository;
    private final PropertyDefinitionRepository propertyDefinitionRepository;
    private final ContentModelDefinitionRepository contentModelDefinitionRepository;

    public TypeDefinition getType(String qualifiedName) {
        String[] parts = parseQualifiedName(qualifiedName);
        return typeDefinitionRepository.findByQualifiedName(parts[0], parts[1])
            .orElseThrow(() -> new NoSuchElementException("Type not found: " + qualifiedName));
    }

    public AspectDefinition getAspect(String qualifiedName) {
        String[] parts = parseQualifiedName(qualifiedName);
        return aspectDefinitionRepository.findByQualifiedName(parts[0], parts[1])
            .orElseThrow(() -> new NoSuchElementException("Aspect not found: " + qualifiedName));
    }

    public List<TypeDefinition> listTypes() {
        return typeDefinitionRepository.findAllActive();
    }

    public List<AspectDefinition> listAspects() {
        return aspectDefinitionRepository.findAllActive();
    }

    public List<PropertyDefinition> getPropertiesForType(String qualifiedName) {
        TypeDefinition type = getType(qualifiedName);
        Map<String, PropertyDefinition> propertyMap = new LinkedHashMap<>();
        resolveTypeProperties(type, propertyMap);
        return new ArrayList<>(propertyMap.values());
    }

    private void resolveTypeProperties(TypeDefinition type, Map<String, PropertyDefinition> propertyMap) {
        if (type.getParentName() != null && !type.getParentName().isBlank()) {
            String[] parts = parseQualifiedName(type.getParentName());
            typeDefinitionRepository.findByQualifiedName(parts[0], parts[1])
                .ifPresent(parent -> resolveTypeProperties(parent, propertyMap));
        }
        for (PropertyDefinition prop : propertyDefinitionRepository.findByTypeDefinitionId(type.getId())) {
            propertyMap.put(prop.getName(), prop);
        }
    }

    public List<PropertyDefinition> getPropertiesForAspect(String qualifiedName) {
        AspectDefinition aspect = getAspect(qualifiedName);
        Map<String, PropertyDefinition> propertyMap = new LinkedHashMap<>();
        resolveAspectProperties(aspect, propertyMap);
        return new ArrayList<>(propertyMap.values());
    }

    private void resolveAspectProperties(AspectDefinition aspect, Map<String, PropertyDefinition> propertyMap) {
        if (aspect.getParentName() != null && !aspect.getParentName().isBlank()) {
            String[] parts = parseQualifiedName(aspect.getParentName());
            aspectDefinitionRepository.findByQualifiedName(parts[0], parts[1])
                .ifPresent(parent -> resolveAspectProperties(parent, propertyMap));
        }
        for (PropertyDefinition prop : propertyDefinitionRepository.findByAspectDefinitionId(aspect.getId())) {
            propertyMap.put(prop.getName(), prop);
        }
    }

    public List<String> getMandatoryAspectsForType(String qualifiedName) {
        TypeDefinition type = getType(qualifiedName);
        return type.getMandatoryAspects() != null ? new ArrayList<>(type.getMandatoryAspects()) : List.of();
    }

    public List<String> resolveTypeHierarchy(String qualifiedName) {
        List<String> hierarchy = new ArrayList<>();
        String current = qualifiedName;
        int maxDepth = 50;
        while (current != null && !current.isBlank() && maxDepth-- > 0) {
            String[] parts = parseQualifiedName(current);
            final String lookupName = current;
            TypeDefinition type = typeDefinitionRepository.findByQualifiedName(parts[0], parts[1])
                .orElseThrow(() -> new NoSuchElementException("Type not found in hierarchy: " + lookupName));
            hierarchy.add(current);
            current = type.getParentName();
        }
        Collections.reverse(hierarchy);
        return hierarchy;
    }

    String[] parseQualifiedName(String qname) {
        if (qname == null || qname.isBlank()) {
            throw new IllegalArgumentException("Qualified name must not be null or blank");
        }
        int colonIndex = qname.indexOf(':');
        if (colonIndex <= 0 || colonIndex >= qname.length() - 1) {
            throw new IllegalArgumentException("Invalid qualified name: '" + qname + "'; expected 'prefix:name'");
        }
        return new String[]{qname.substring(0, colonIndex), qname.substring(colonIndex + 1)};
    }
}
