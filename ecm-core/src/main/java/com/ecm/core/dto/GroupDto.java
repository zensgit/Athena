package com.ecm.core.dto;

import java.util.List;

public record GroupDto(
    String id,
    String name,
    String displayName,
    String description,
    String email,
    Boolean enabled,
    String groupType,
    List<UserDto> users
) {}

