package com.ecm.core.dto;

import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.ModelStatus;

import java.util.List;
import java.util.UUID;

public record ContentModelDefinitionDto(
    UUID id,
    String namespaceUri,
    String prefix,
    String name,
    String description,
    String author,
    ModelStatus status,
    String versionLabel,
    List<TypeDefinitionDto> types,
    List<AspectDefinitionDto> aspects
) {

    public static ContentModelDefinitionDto from(ContentModelDefinition model) {
        return new ContentModelDefinitionDto(
            model.getId(),
            model.getNamespaceUri(),
            model.getPrefix(),
            model.getName(),
            model.getDescription(),
            model.getAuthor(),
            model.getStatus(),
            model.getVersionLabel(),
            model.getTypes() == null ? List.of() : model.getTypes().stream().map(TypeDefinitionDto::from).toList(),
            model.getAspects() == null ? List.of() : model.getAspects().stream().map(AspectDefinitionDto::from).toList()
        );
    }
}
