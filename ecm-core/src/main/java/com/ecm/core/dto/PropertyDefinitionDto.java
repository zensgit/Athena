package com.ecm.core.dto;

import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.entity.PropertyDefinition;

import java.util.List;
import java.util.UUID;

public record PropertyDefinitionDto(
    UUID id,
    String name,
    String title,
    String description,
    PropertyDataType dataType,
    boolean mandatory,
    boolean multiValued,
    String defaultValue,
    boolean indexed,
    boolean protectedField,
    String qualifiedName,
    List<ConstraintDefinitionDto> constraints
) {

    public static PropertyDefinitionDto from(PropertyDefinition property) {
        return new PropertyDefinitionDto(
            property.getId(),
            property.getName(),
            property.getTitle(),
            property.getDescription(),
            property.getDataType(),
            property.isMandatory(),
            property.isMultiValued(),
            property.getDefaultValue(),
            property.isIndexed(),
            property.isProtectedField(),
            property.qualifiedName(),
            property.getConstraints() == null
                ? List.of()
                : property.getConstraints().stream().map(ConstraintDefinitionDto::from).toList()
        );
    }
}
