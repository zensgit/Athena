package com.ecm.core.controller;

import com.ecm.core.integration.oauth.OAuthCredentialAdminService;
import com.ecm.core.integration.oauth.OAuthCredentialInventoryItem;
import com.ecm.core.integration.oauth.OAuthProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/oauth-credentials")
@RequiredArgsConstructor
@Tag(name = "OAuth Credential Admin", description = "Read-only OAuth credential inventory without token disclosure")
@PreAuthorize("hasRole('ADMIN')")
public class OAuthCredentialAdminController {

    private final OAuthCredentialAdminService oauthCredentialAdminService;

    @GetMapping
    @Operation(
        summary = "List OAuth credential inventory",
        description = "Returns owner/provider/configuration status for OAuth credentials without exposing access or refresh tokens."
    )
    public ResponseEntity<List<OAuthCredentialInventoryItem>> listCredentials(
        @RequestParam(required = false) String ownerType,
        @RequestParam(required = false) OAuthProviderType provider
    ) {
        return ResponseEntity.ok(oauthCredentialAdminService.listCredentials(ownerType, provider));
    }

    @PostMapping("/{credentialId}/require-reauth")
    @Operation(
        summary = "Require OAuth reauthorization",
        description = "Clears locally stored OAuth access and refresh tokens for a credential owner without calling the remote provider."
    )
    public ResponseEntity<OAuthCredentialInventoryItem> requireReauth(@PathVariable UUID credentialId) {
        return ResponseEntity.ok(oauthCredentialAdminService.requireReauth(credentialId));
    }
}
