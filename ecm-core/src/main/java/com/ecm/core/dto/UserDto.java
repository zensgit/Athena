package com.ecm.core.dto;

import java.util.List;

public record UserDto(
    String id,
    String username,
    String email,
    List<String> roles,
    String firstName,
    String lastName,
    Boolean enabled,
    Boolean locked
) {}

