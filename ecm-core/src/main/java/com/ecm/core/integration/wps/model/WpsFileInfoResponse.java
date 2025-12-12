package com.ecm.core.integration.wps.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WpsFileInfoResponse {
    
    private FileInfo file;
    private UserInfo user;
    
    @Data
    @Builder
    public static class FileInfo {
        private String id;
        private String name;
        private int version;
        private long size;
        private String creator;
        private String modifier;
        @JsonProperty("create_time")
        private long createTime;
        @JsonProperty("modify_time")
        private long modifyTime;
        
        // Permissions
        @JsonProperty("user_acl")
        private UserAcl userAcl;
        @JsonProperty("watermark")
        private Watermark watermark;
    }
    
    @Data
    @Builder
    public static class UserAcl {
        private int rename; // 1: allow, 0: deny
        private int history;
        private int copy;
        private int export;
        private int print;
    }
    
    @Data
    @Builder
    public static class Watermark {
        private int type; // 0: none, 1: text
        private String value;
        private String fillstyle;
        private String font;
    }
    
    @Data
    @Builder
    public static class UserInfo {
        private String id;
        private String name;
        @JsonProperty("permission")
        private String permission; // "read", "write"
        @JsonProperty("avatar_url")
        private String avatarUrl;
    }
}
