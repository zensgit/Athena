package com.ecm.core.integration.wps.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Permission;
import com.ecm.core.integration.wps.model.WpsFileInfoResponse;
import com.ecm.core.integration.wps.model.WpsFileInfoResponse.FileInfo;
import com.ecm.core.integration.wps.model.WpsFileInfoResponse.UserAcl;
import com.ecm.core.integration.wps.model.WpsFileInfoResponse.UserInfo;
import com.ecm.core.integration.wps.model.WpsSaveRequest;
import com.ecm.core.integration.wps.model.WpsUrlResponse;
import com.ecm.core.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Service for WPS Web Office Integration.
 * 
 * Implements the callback logic required by WPS Web Office to view and edit files.
 * Referencing Alfresco's pattern:
 * 1. Check permissions (SecurityService)
 * 2. Handle locking (NodeService)
 * 3. Handle versioning (VersionService)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WpsIntegrationService {

    private final NodeService nodeService;
    private final SecurityService securityService;
    private final VersionService versionService;
    private final ContentService contentService;
    private final AuditService auditService;

    @Value("${ecm.wps.appid:athena_ecm}")
    private String appId;

    @Value("${ecm.wps.appkey:secret_key}")
    private String appKey;

    @Value("${ecm.wps.domain:https://wwo.wps.cn/office/}")
    private String wpsDomain;
    
    @Value("${ecm.api.base-url:http://localhost:8080}")
    private String apiBaseUrl;

    @Value("${ecm.wps.enabled:false}")
    private boolean wpsEnabled;

    /**
     * Generate the Web Office URL for frontend iframe.
     */
    public WpsUrlResponse generateWebOfficeUrl(UUID documentId, String permission) {
        ensureWpsEnabledAndConfigured();

        Document document = (Document) nodeService.getNode(documentId);
        String currentUser = securityService.getCurrentUser();

        // Check permissions
        if ("write".equals(permission)) {
            securityService.checkPermission(document, Permission.PermissionType.WRITE);
        } else {
            securityService.checkPermission(document, Permission.PermissionType.READ);
        }

        // Generate a temporary access token (simplified for MVP: using JWT or a random string stored in Redis)
        // For this implementation, we'll sign parameters directly as per WPS standard often requiring signatures
        // Here we return a constructed URL that the frontend can use
        
        String fileType = getFileType(document.getName());
        String token = UUID.randomUUID().toString(); // In prod, store this token + user + doc context in Redis
        
        // Construct WPS specific URL structure (Example: standard WebOffice protocol)
        // https://wwo.wps.cn/office/[type]/[fileId]?_w_appid=xxx&_w_signature=xxx
        
        String type = getWpsType(fileType); // w/s/p/f
        String domain = wpsDomain != null && wpsDomain.endsWith("/") ? wpsDomain : (wpsDomain + "/");
        String encodedFilePath = URLEncoder.encode(document.getName(), StandardCharsets.UTF_8);
        String wpsUrl = String.format("%s%s/%s?_w_appid=%s&_w_tokentype=1&_w_filepath=%s",
            domain, type, documentId, appId, encodedFilePath);

        return WpsUrlResponse.builder()
            .wpsUrl(wpsUrl)
            .token(token) // This token should be validated in the callback
            .expiresAt(System.currentTimeMillis() + 3600000)
            .build();
    }

    /**
     * Callback: Get File Info
     */
    @Transactional(readOnly = true)
    public WpsFileInfoResponse getFileInfo(String fileId, String token) {
        // In real impl: Validate token
        
        Document doc = (Document) nodeService.getNode(UUID.fromString(fileId));
        String currentUser = securityService.getCurrentUser(); // This might need to come from token context if stateless

        UserAcl acl = UserAcl.builder()
            .rename(0)
            .history(1)
            .copy(1)
            .export(1)
            .print(1)
            .build();

        FileInfo fileInfo = FileInfo.builder()
            .id(doc.getId().toString())
            .name(doc.getName())
            .version(1) // Map to actual version
            .size(doc.getSize() != null ? doc.getSize() : 0)
            .creator(doc.getCreatedBy())
            .modifier(doc.getLastModifiedBy())
            .createTime(doc.getCreatedDate().toEpochSecond(ZoneOffset.UTC))
            .modifyTime(doc.getLastModifiedDate().toEpochSecond(ZoneOffset.UTC))
            .userAcl(acl)
            .build();

        UserInfo userInfo = UserInfo.builder()
            .id(currentUser)
            .name(currentUser)
            .permission(securityService.hasPermission(doc, Permission.PermissionType.WRITE) ? "write" : "read")
            .avatarUrl("default_avatar")
            .build();

        return WpsFileInfoResponse.builder()
            .file(fileInfo)
            .user(userInfo)
            .build();
    }

    /**
     * Callback: Save File
     */
    @Transactional
    public Map<String, Object> saveFile(String fileId, MultipartFile file, String userId) throws IOException {
        log.info("WPS Save callback for file {}", fileId);
        
        UUID documentId = UUID.fromString(fileId);
        
        // Use existing VersionService to create a new version
        // This aligns with Alfresco's pattern of "Checkin" or "Update Version"
        versionService.createVersion(documentId, file, "Updated via WPS Online", false);
        
        Document doc = (Document) nodeService.getNode(documentId);
        
        // Return required response for WPS
        return Map.of(
            "file", Map.of(
                "id", doc.getId(),
                "name", doc.getName(),
                "version", doc.getVersionLabel() != null ? Integer.parseInt(doc.getVersionLabel().replace(".", "")) : 2,
                "size", doc.getSize(),
                "download_url", apiBaseUrl + "/api/v1/documents/" + doc.getId() + "/download"
            )
        );
    }

    private String getFileType(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1).toLowerCase();
    }

    private String getWpsType(String ext) {
        return switch (ext) {
            case "doc", "docx", "dot", "dotx", "wps", "wpt" -> "w";
            case "xls", "xlsx", "xlt", "xltx", "et", "ett" -> "s";
            case "ppt", "pptx", "pot", "potx", "dps", "dpt" -> "p";
            case "pdf" -> "f";
            default -> "w";
        };
    }

    private void ensureWpsEnabledAndConfigured() {
        if (!wpsEnabled) {
            throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "WPS online editing is disabled. Set ecm.wps.enabled=true and configure ecm.wps.appid/ecm.wps.appkey."
            );
        }

        boolean defaultAppId = appId == null || appId.isBlank() || "athena_ecm".equals(appId);
        boolean defaultAppKey = appKey == null || appKey.isBlank() || "secret_key".equals(appKey);

        if (defaultAppId || defaultAppKey) {
            throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "WPS online editing is not configured (invalid appId/appKey). Configure ecm.wps.appid/ecm.wps.appkey to avoid AppInfoNotExists."
            );
        }
    }
}
