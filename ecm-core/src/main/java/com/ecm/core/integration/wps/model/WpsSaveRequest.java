package com.ecm.core.integration.wps.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WpsSaveRequest {
    private String name;
    private int version;
    private long size;
    @JsonProperty("download_url")
    private String downloadUrl;
}
