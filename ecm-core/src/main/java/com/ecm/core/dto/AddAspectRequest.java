package com.ecm.core.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record AddAspectRequest(
    @NotBlank(message = "Aspect name is required")
    String aspectName,

    Map<String, Object> properties
) {
}
