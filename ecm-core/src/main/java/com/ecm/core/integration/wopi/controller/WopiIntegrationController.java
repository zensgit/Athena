package com.ecm.core.integration.wopi.controller;

import com.ecm.core.integration.wopi.model.WopiHealthResponse;
import com.ecm.core.integration.wopi.service.WopiEditorService;
import com.ecm.core.integration.wopi.service.WopiEditorService.WopiUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integration/wopi")
@RequiredArgsConstructor
@Tag(name = "Integration: WOPI", description = "WOPI editor URL for Collabora/OnlyOffice")
public class WopiIntegrationController {

    private final WopiEditorService wopiEditorService;

    @GetMapping("/health")
    @Operation(summary = "WOPI integration health", description = "Validate Collabora discovery/capabilities and current configuration")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WopiHealthResponse> health() {
        return ResponseEntity.ok(wopiEditorService.getHealth());
    }

    @GetMapping("/url/{documentId}")
    @Operation(summary = "Get WOPI editor URL", description = "Generate Collabora/OnlyOffice editor URL for the document")
    public ResponseEntity<WopiUrlResponse> getEditorUrl(
        @PathVariable UUID documentId,
        @RequestParam(defaultValue = "read") String permission
    ) {
        return ResponseEntity.ok(wopiEditorService.generateEditorUrl(documentId, permission));
    }
}
