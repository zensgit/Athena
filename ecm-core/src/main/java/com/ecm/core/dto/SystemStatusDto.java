package com.ecm.core.dto;

import com.ecm.core.integration.wopi.model.WopiHealthResponse;

import java.util.Map;

public record SystemStatusDto(
    String timestamp,
    Map<String, Object> database,
    Map<String, Object> redis,
    Map<String, Object> rabbitmq,
    Map<String, Object> search,
    Map<String, Object> ml,
    Map<String, Object> keycloak,
    WopiHealthResponse wopi,
    Map<String, Object> antivirus
) {}

