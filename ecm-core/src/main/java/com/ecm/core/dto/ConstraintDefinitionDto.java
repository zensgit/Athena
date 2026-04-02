package com.ecm.core.dto;

import com.ecm.core.entity.ConstraintDefinition;
import com.ecm.core.entity.ConstraintType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ConstraintDefinitionDto(
    UUID id,
    ConstraintType constraintType,
    Map<String, Object> parameters
) {

    public static ConstraintDefinitionDto from(ConstraintDefinition constraint) {
        return new ConstraintDefinitionDto(
            constraint.getId(),
            constraint.getConstraintType(),
            constraint.getParameters() == null ? Map.of() : new LinkedHashMap<>(constraint.getParameters())
        );
    }
}
