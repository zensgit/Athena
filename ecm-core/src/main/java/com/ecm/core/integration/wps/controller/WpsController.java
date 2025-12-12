package com.ecm.core.integration.wps.controller;

import com.ecm.core.integration.wps.model.WpsFileInfoResponse;
import com.ecm.core.integration.wps.model.WpsUrlResponse;
import com.ecm.core.integration.wps.service.WpsIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for WPS Web Office integration.
 * Provides endpoints for the frontend to get the editor URL,
 * and callbacks for the WPS server to read/write files.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/integration/wps")
@RequiredArgsConstructor
@Tag(name = "Integration: WPS", description = "WPS Web Office integration")
public class WpsController {

    private final WpsIntegrationService wpsService;

    // === Client Facing API ===

    @GetMapping("/url/{documentId}")
    @Operation(summary = "Get Editor URL", description = "Generate WPS Web Office URL for the document")
    public ResponseEntity<WpsUrlResponse> getEditorUrl(
            @PathVariable UUID documentId,
            @RequestParam(defaultValue = "read") String permission) {
        
        return ResponseEntity.ok(wpsService.generateWebOfficeUrl(documentId, permission));
    }

    // === WPS Callback API (3rd party interface) ===
    // Note: In production, these should be secured by a specific token mechanism or IP whitelist
    // checking specific headers sent by WPS like "x-wps-signature"

    @GetMapping("/v1/3rd/file/info")
    @Operation(summary = "Callback: File Info", description = "WPS calls this to get file metadata")
    public ResponseEntity<WpsFileInfoResponse> getFileInfo(
            @RequestParam("_w_file_id") String fileId,
            @RequestHeader(value = "x-wps-weboffice-token", required = false) String token) {
        
        return ResponseEntity.ok(wpsService.getFileInfo(fileId, token));
    }

    @GetMapping("/v1/3rd/user/info")
    @Operation(summary = "Callback: User Info", description = "WPS calls this to get user info")
    public ResponseEntity<Map<String, Object>> getUserInfo(
            @RequestParam("_w_file_id") String fileId,
            @RequestHeader(value = "x-wps-weboffice-token", required = false) String token) {
        
        // Reuse file info to get user part or separate logic
        WpsFileInfoResponse response = wpsService.getFileInfo(fileId, token);
        return ResponseEntity.ok(Map.of("id", response.getUser().getId(), "name", response.getUser().getName(), "avatar_url", response.getUser().getAvatarUrl()));
    }

    @PostMapping("/v1/3rd/file/save")
    @Operation(summary = "Callback: Save File", description = "WPS calls this to save the file")
    public ResponseEntity<Map<String, Object>> saveFile(
            @RequestParam("_w_file_id") String fileId,
            @RequestParam("_w_userid") String userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        
        return ResponseEntity.ok(wpsService.saveFile(fileId, file, userId));
    }
}
