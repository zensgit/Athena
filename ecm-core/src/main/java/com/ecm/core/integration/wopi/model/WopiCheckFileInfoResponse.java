package com.ecm.core.integration.wopi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WopiCheckFileInfoResponse {
    
    @JsonProperty("BaseFileName")
    private String baseFileName;
    
    @JsonProperty("OwnerId")
    private String ownerId;
    
    @JsonProperty("Size")
    private long size;
    
    @JsonProperty("UserId")
    private String userId;

    @JsonProperty("UserFriendlyName")
    private String userFriendlyName;
    
    @JsonProperty("Version")
    private String version;
    
    // Permissions
    @JsonProperty("UserCanWrite")
    private boolean userCanWrite;
    
    @JsonProperty("UserCanRename")
    private boolean userCanRename;
    
    @JsonProperty("ReadOnly")
    private boolean readOnly;
    
    // URLs (Optional)
    @JsonProperty("DownloadUrl")
    private String downloadUrl;

    @JsonProperty("PostMessageOrigin")
    private String postMessageOrigin;
    
    // Branding
    @JsonProperty("BreadcrumbBrandName")
    private String breadcrumbBrandName;
    
    @JsonProperty("BreadcrumbDocName")
    private String breadcrumbDocName;
    
    // Security
    @JsonProperty("SupportsLocks")
    private boolean supportsLocks;
    
    @JsonProperty("SupportsUpdate")
    private boolean supportsUpdate;
    
    @JsonProperty("SupportsCobalt")
    private boolean supportsCobalt; // For MS Office specific optimizations
}
