package com.ecm.core.integration.wopi.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WopiHealthResponse {
    private boolean enabled;
    private String wopiHostUrl;
    private String discoveryUrl;
    private String capabilitiesUrl;
    private String publicUrl;
    private CollaboraDiscoveryStatus discovery;
    private CollaboraCapabilities capabilities;
}

