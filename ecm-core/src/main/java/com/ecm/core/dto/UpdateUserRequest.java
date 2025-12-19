package com.ecm.core.dto;

public record UpdateUserRequest(
    String email,
    String firstName,
    String lastName,
    Boolean enabled
) {}

