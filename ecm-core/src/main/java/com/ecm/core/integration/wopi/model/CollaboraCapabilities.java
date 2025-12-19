package com.ecm.core.integration.wopi.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CollaboraCapabilities {
    private boolean reachable;
    private String productName;
    private String productVersion;
    private String productVersionHash;
    private String serverId;
    private Boolean hasWopiAccessCheck;
    private Boolean hasSettingIframeSupport;
    private String error;
}

