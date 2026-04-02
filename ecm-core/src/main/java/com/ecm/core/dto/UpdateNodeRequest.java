package com.ecm.core.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateNodeRequest(
    @Size(max = 255, message = "Node name must be at most 255 characters")
    String name,

    String description,

    Map<String, Object> properties,

    Map<String, Object> metadata,

    String correspondentId
) {
}
