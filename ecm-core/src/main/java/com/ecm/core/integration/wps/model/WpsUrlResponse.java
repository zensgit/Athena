package com.ecm.core.integration.wps.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WpsUrlResponse {
    private String wpsUrl;
    private String token;
    private long expiresAt;
}
