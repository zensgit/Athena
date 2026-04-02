package com.ecm.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateNodeRequest(
    @NotBlank(message = "Node name is required")
    @Size(max = 255, message = "Node name must be at most 255 characters")
    String name,

    String description,

    @NotBlank(message = "Node type is required")
    String nodeType,

    String mimeType,

    String typeQName,

    Map<String, Object> properties,

    Map<String, Object> metadata,

    List<String> aspects
) {
}
