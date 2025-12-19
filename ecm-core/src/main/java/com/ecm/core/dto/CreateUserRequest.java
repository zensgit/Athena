package com.ecm.core.dto;

public record CreateUserRequest(
    String username,
    String email,
    String password,
    String firstName,
    String lastName,
    Boolean enabled
) {}

