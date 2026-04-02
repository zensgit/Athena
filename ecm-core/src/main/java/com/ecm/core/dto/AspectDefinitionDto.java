package com.ecm.core.dto;

import com.ecm.core.entity.AspectDefinition;

import java.util.List;
import java.util.UUID;

public record AspectDefinitionDto(
    UUID id,
    String name,
    String title,
    String description,
    String parentName,
    String qualifiedName,
    List<PropertyDefinitionDto> properties
) {

    public static AspectDefinitionDto from(AspectDefinition aspect) {
        return new AspectDefinitionDto(
            aspect.getId(),
            aspect.getName(),
            aspect.getTitle(),
            aspect.getDescription(),
            aspect.getParentName(),
            aspect.qualifiedName(),
            aspect.getProperties() == null ? List.of() : aspect.getProperties().stream().map(PropertyDefinitionDto::from).toList()
        );
    }
}
