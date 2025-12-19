package com.ecm.core.integration.wopi.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CollaboraDiscoveryStatus {
    private boolean reachable;
    private long lastLoadedAtMs;
    private long cacheTtlSeconds;
    private int extensionCount;
    private Map<String, List<String>> sampleActionsByExtension;
    private String lastError;
}

