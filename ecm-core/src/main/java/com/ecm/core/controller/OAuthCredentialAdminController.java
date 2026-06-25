package com.ecm.core.controller;

import com.ecm.core.integration.oauth.OAuthCredentialAdminService;
import com.ecm.core.integration.oauth.OAuthCredentialInventoryItem;
import com.ecm.core.integration.oauth.OAuthCredentialRevokeEndpointDetails;
import com.ecm.core.integration.oauth.OAuthProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/{credentialId}/refresh-now")
    @Operation(
        summary = "Refresh OAuth credential now",
        description = "Forces an OAuth refresh-token grant for the credential owner and returns the redacted inventory row."
    )
    public ResponseEntity<OAuthCredentialInventoryItem> refreshNow(@PathVariable UUID credentialId) {
        return ResponseEntity.ok(oauthCredentialAdminService.refreshNow(credentialId));
    }

    @PostMapping("/{credentialId}/revoke")
    @Operation(
        summary = "Revoke or locally clear OAuth credential",
        description = "Calls the provider's revoke endpoint for GOOGLE and configured CUSTOM credentials. "
            + "MICROSOFT performs a local token clear only because Entra has no per-token revoke endpoint. "
            + "On success or already-invalid-token "
            + "responses, clears local tokens and returns the redacted inventory row. On 5xx or network failure, "
            + "preserves local tokens and surfaces a diagnostic error."
    )
    public ResponseEntity<OAuthCredentialInventoryItem> revoke(@PathVariable UUID credentialId) {
        return ResponseEntity.ok(oauthCredentialAdminService.revokeProvider(credentialId));
    }

    @PutMapping("/{credentialId}/revoke-endpoint")
    @Operation(
        summary = "Configure CUSTOM OAuth revoke endpoint",
        description = "Stores or clears the HTTPS RFC 7009-style revoke endpoint for a CUSTOM OAuth credential and "
            + "returns the redacted inventory row with refreshed provider-revoke capability metadata."
    )
    public ResponseEntity<OAuthCredentialInventoryItem> updateRevokeEndpoint(
        @PathVariable UUID credentialId,
        @RequestBody UpdateRevokeEndpointRequest request
    ) {
        return ResponseEntity.ok(oauthCredentialAdminService.updateRevokeEndpoint(
            credentialId,
            request == null ? null : request.revokeEndpoint()
        ));
    }

    @GetMapping("/{credentialId}/revoke-endpoint")
    @Operation(
        summary = "Read CUSTOM OAuth revoke endpoint details",
        description = "Returns the persisted CUSTOM revoke endpoint only for an explicit admin detail request. "
            + "Inventory responses remain redacted and continue to expose only the boolean configuration flag."
    )
    public ResponseEntity<OAuthCredentialRevokeEndpointDetails> getRevokeEndpointDetails(
        @PathVariable UUID credentialId
    ) {
        return ResponseEntity.ok(oauthCredentialAdminService.getRevokeEndpointDetails(credentialId));
    }

    public record UpdateRevokeEndpointRequest(String revokeEndpoint) {
    }
}
