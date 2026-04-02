package com.ecm.core.dto;

import com.ecm.core.entity.TypeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TypeDefinitionDto(
    UUID id,
    String name,
    String title,
    String description,
    String parentName,
    String qualifiedName,
    List<String> mandatoryAspects,
    List<PropertyDefinitionDto> properties
) {

    public static TypeDefinitionDto from(TypeDefinition type) {
        return new TypeDefinitionDto(
            type.getId(),
            type.getName(),
            type.getTitle(),
            type.getDescription(),
            type.getParentName(),
            type.qualifiedName(),
            type.getMandatoryAspects() == null ? List.of() : new ArrayList<>(type.getMandatoryAspects()),
            type.getProperties() == null ? List.of() : type.getProperties().stream().map(PropertyDefinitionDto::from).toList()
        );
    }
}
