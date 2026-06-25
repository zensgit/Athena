package com.ecm.core.integration.oauth;

import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.integration.oauth.model.OAuthCredential;
import com.ecm.core.integration.oauth.repository.OAuthCredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthCredentialAdminServiceTest {

    @Mock
    private OAuthCredentialRepository oauthCredentialRepository;

    @Mock
    private OAuthCredentialService oauthCredentialService;

    @InjectMocks
    private OAuthCredentialAdminService oauthCredentialAdminService;

    @Test
    @DisplayName("listCredentials normalizes blank owner type filter")
    void listCredentialsNormalizesBlankOwnerTypeFilter() {
        when(oauthCredentialRepository.findInventoryItems(null, OAuthProviderType.GOOGLE)).thenReturn(List.of());

        List<OAuthCredentialInventoryItem> result = oauthCredentialAdminService.listCredentials("  ", OAuthProviderType.GOOGLE);

        assertTrue(result.isEmpty());
        verify(oauthCredentialRepository).findInventoryItems(null, OAuthProviderType.GOOGLE);
    }

    @Test
    @DisplayName("listCredentials trims owner type filter")
    void listCredentialsTrimsOwnerTypeFilter() {
        when(oauthCredentialRepository.findInventoryItems("MAIL_ACCOUNT", null)).thenReturn(List.of());

        oauthCredentialAdminService.listCredentials(" MAIL_ACCOUNT ", null);

        verify(oauthCredentialRepository).findInventoryItems("MAIL_ACCOUNT", null);
    }

    @Test
    @DisplayName("listCredentials enriches every row with provider-revoke capability metadata")
    void listCredentialsEnrichesEveryRowWithCapability() {
        // Two rows: a GOOGLE row with provider revoke and a MICROSOFT row with local-clear.
        // The repository projection always returns capability-default (false, null), so the service
        // must re-derive both fields per row before returning to the controller.
        UUID googleId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID microsoftId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        OAuthCredentialInventoryItem googleRow = inventory(googleId, OAuthProviderType.GOOGLE, true, true, true);
        OAuthCredentialInventoryItem microsoftRow = inventory(microsoftId, OAuthProviderType.MICROSOFT, true, true, true);
        when(oauthCredentialRepository.findInventoryItems(null, null)).thenReturn(List.of(googleRow, microsoftRow));

        List<OAuthCredentialInventoryItem> result = oauthCredentialAdminService.listCredentials(null, null);

        assertEquals(2, result.size());
        assertTrue(result.get(0).providerRevokeSupported());
        assertNull(result.get(0).providerRevokeUnsupportedReason());
        assertEquals(OAuthRevokeCapabilityMode.PROVIDER_REVOKE, result.get(0).providerRevokeMode());
        assertTrue(result.get(1).providerRevokeSupported());
        assertNull(result.get(1).providerRevokeUnsupportedReason());
        assertEquals(OAuthRevokeCapabilityMode.LOCAL_CLEAR, result.get(1).providerRevokeMode());
    }

    @Test
    @DisplayName("listCredentials uses runtime capability metadata for CUSTOM rows")
    void listCredentialsUsesRuntimeCapabilityForCustomRows() {
        UUID customId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        OAuthCredentialInventoryItem customRow = inventory(customId, OAuthProviderType.CUSTOM, true, false, true);
        when(oauthCredentialRepository.findInventoryItems(null, OAuthProviderType.CUSTOM)).thenReturn(List.of(customRow));
        when(oauthCredentialService.providerRevokeCapability("MAIL_ACCOUNT", customRow.ownerId()))
            .thenReturn(new OAuthProviderRevokeCapability(true, null));

        List<OAuthCredentialInventoryItem> result = oauthCredentialAdminService.listCredentials(null, OAuthProviderType.CUSTOM);

        assertEquals(1, result.size());
        assertTrue(result.get(0).providerRevokeSupported());
        assertNull(result.get(0).providerRevokeUnsupportedReason());
        assertEquals(OAuthRevokeCapabilityMode.PROVIDER_REVOKE, result.get(0).providerRevokeMode());
    }

    @Test
    @DisplayName("withCapability: GOOGLE with stored token is supported, reason null")
    void withCapabilityGoogleWithStoredTokenIsSupported() {
        OAuthCredentialInventoryItem item = inventory(UUID.randomUUID(), OAuthProviderType.GOOGLE, true, true, true);

        OAuthCredentialInventoryItem enriched = OAuthCredentialAdminService.withCapability(item);

        assertTrue(enriched.providerRevokeSupported());
        assertNull(enriched.providerRevokeUnsupportedReason());
        assertEquals(OAuthRevokeCapabilityMode.PROVIDER_REVOKE, enriched.providerRevokeMode());
    }

    @Test
    @DisplayName("withCapability: GOOGLE with refresh-only token is supported")
    void withCapabilityGoogleWithRefreshOnlyTokenIsSupported() {
        OAuthCredentialInventoryItem item = inventory(UUID.randomUUID(), OAuthProviderType.GOOGLE, true, false, true);

        OAuthCredentialInventoryItem enriched = OAuthCredentialAdminService.withCapability(item);

        assertTrue(enriched.providerRevokeSupported());
        assertNull(enriched.providerRevokeUnsupportedReason());
        assertEquals(OAuthRevokeCapabilityMode.PROVIDER_REVOKE, enriched.providerRevokeMode());
    }

    @Test
    @DisplayName("withCapability: GOOGLE with no stored token but credentialKey says env-managed")
    void withCapabilityGoogleNoTokenWithCredentialKeyIsEnvManaged() {
        // Reason text must match OAuthCredentialService.revokeProviderTokens byte-for-byte
        // so the operator sees identical messaging via projection and via POST /revoke errors.
        OAuthCredentialInventoryItem item = inventory(UUID.randomUUID(), OAuthProviderType.GOOGLE, true, false, false);

        OAuthCredentialInventoryItem enriched = OAuthCredentialAdminService.withCapability(item);

        assertFalse(enriched.providerRevokeSupported());
        assertEquals(
            "Provider-side revoke requires a locally stored OAuth token; "
                + "this credential row only references env-managed secrets",
            enriched.providerRevokeUnsupportedReason()
        );
        assertEquals(OAuthRevokeCapabilityMode.UNSUPPORTED, enriched.providerRevokeMode());
    }

    @Test
    @DisplayName("withCapability: GOOGLE with no stored token and no credentialKey says no token")
    void withCapabilityGoogleNoTokenNoCredentialKeyIsNoToken() {
        OAuthCredentialInventoryItem item = inventory(UUID.randomUUID(), OAuthProviderType.GOOGLE, false, false, false);

        OAuthCredentialInventoryItem enriched = OAuthCredentialAdminService.withCapability(item);

        assertFalse(enriched.providerRevokeSupported());
        assertEquals("No locally stored OAuth token to revoke", enriched.providerRevokeUnsupportedReason());
        assertEquals(OAuthRevokeCapabilityMode.UNSUPPORTED, enriched.providerRevokeMode());
    }

    @Test
    @DisplayName("withCapability: MICROSOFT with stored token is local-clear")
    void withCapabilityMicrosoftWithStoredTokenIsLocalClear() {
        OAuthCredentialInventoryItem item = inventory(UUID.randomUUID(), OAuthProviderType.MICROSOFT, true, true, true);

        OAuthCredentialInventoryItem enriched = OAuthCredentialAdminService.withCapability(item);

        assertTrue(enriched.providerRevokeSupported());
        assertNull(enriched.providerRevokeUnsupportedReason());
        assertEquals(OAuthRevokeCapabilityMode.LOCAL_CLEAR, enriched.providerRevokeMode());
    }

    @Test
    @DisplayName("withCapability: CUSTOM with stored token defaults to endpoint-not-configured")
    void withCapabilityCustomIsUnsupported() {
        OAuthCredentialInventoryItem item = inventory(UUID.randomUUID(), OAuthProviderType.CUSTOM, false, true, false);

        OAuthCredentialInventoryItem enriched = OAuthCredentialAdminService.withCapability(item);

        assertFalse(enriched.providerRevokeSupported());
        assertEquals(
            "Provider-side revoke endpoint is not configured for this CUSTOM credential",
            enriched.providerRevokeUnsupportedReason()
        );
        assertEquals(OAuthRevokeCapabilityMode.UNSUPPORTED, enriched.providerRevokeMode());
    }

    @Test
    @DisplayName("withCapability: null provider is unsupported and renders as 'null' in reason")
    void withCapabilityNullProviderIsUnsupported() {
        // Matches the literal string thrown by OAuthCredentialService.revokeProviderTokens
        // when provider() is null: "...is " + null -> "...is null".
        OAuthCredentialInventoryItem item = inventory(UUID.randomUUID(), null, true, true, true);

        OAuthCredentialInventoryItem enriched = OAuthCredentialAdminService.withCapability(item);

        assertFalse(enriched.providerRevokeSupported());
        assertEquals(
            "OAuth revoke is only supported for GOOGLE, MICROSOFT, or CUSTOM; this credential is null",
            enriched.providerRevokeUnsupportedReason()
        );
        assertEquals(OAuthRevokeCapabilityMode.UNSUPPORTED, enriched.providerRevokeMode());
    }

    @Test
    @DisplayName("requireReauth clears owner tokens and returns redacted inventory with capability metadata")
    void requireReauthClearsOwnerTokensAndReturnsRedactedInventory() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OAuthCredentialInventoryItem item = new OAuthCredentialInventoryItem(
            credentialId,
            "MAIL_ACCOUNT",
            ownerId,
            OAuthProviderType.GOOGLE,
            true,
            false,
            true,
            true,
            false,
            false,
            false,
            null,
            LocalDateTime.parse("2026-05-01T09:00:00"),
            LocalDateTime.parse("2026-05-06T10:00:00"),
            false,
            null
        );
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId))
            .thenReturn(Optional.of(new OAuthCredentialOwnerReference(credentialId, "MAIL_ACCOUNT", ownerId)));
        when(oauthCredentialRepository.findInventoryItemById(credentialId)).thenReturn(Optional.of(item));

        OAuthCredentialInventoryItem result = oauthCredentialAdminService.requireReauth(credentialId);

        // After require-reauth the token state is wiped; capability falls into the
        // env-managed-secrets branch (credentialKey configured, no stored token).
        assertFalse(result.providerRevokeSupported());
        assertEquals(
            "Provider-side revoke requires a locally stored OAuth token; "
                + "this credential row only references env-managed secrets",
            result.providerRevokeUnsupportedReason()
        );
        verify(oauthCredentialService).clearTokens("MAIL_ACCOUNT", ownerId);
    }

    @Test
    @DisplayName("requireReauth rejects unknown credential id")
    void requireReauthRejectsUnknownCredentialId() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> oauthCredentialAdminService.requireReauth(credentialId));
    }

    @Test
    @DisplayName("refreshNow refreshes owner token and returns redacted inventory with capability metadata")
    void refreshNowRefreshesOwnerTokenAndReturnsRedactedInventory() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OAuthCredentialInventoryItem item = new OAuthCredentialInventoryItem(
            credentialId,
            "MAIL_ACCOUNT",
            ownerId,
            OAuthProviderType.GOOGLE,
            true,
            false,
            true,
            true,
            true,
            true,
            true,
            LocalDateTime.parse("2026-05-06T12:00:00"),
            LocalDateTime.parse("2026-05-01T09:00:00"),
            LocalDateTime.parse("2026-05-06T11:00:00"),
            false,
            null
        );
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId))
            .thenReturn(Optional.of(new OAuthCredentialOwnerReference(credentialId, "MAIL_ACCOUNT", ownerId)));
        when(oauthCredentialRepository.findInventoryItemById(credentialId)).thenReturn(Optional.of(item));

        OAuthCredentialInventoryItem result = oauthCredentialAdminService.refreshNow(credentialId);

        assertTrue(result.providerRevokeSupported());
        assertNull(result.providerRevokeUnsupportedReason());
        verify(oauthCredentialService).refreshAccessTokenNow("MAIL_ACCOUNT", ownerId);
    }

    @Test
    @DisplayName("refreshNow rejects unknown credential id")
    void refreshNowRejectsUnknownCredentialId() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> oauthCredentialAdminService.refreshNow(credentialId));
    }

    @Test
    @DisplayName("revokeProvider revokes owner tokens at provider and returns redacted inventory with capability metadata")
    void revokeProviderRevokesOwnerTokensAndReturnsRedactedInventory() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OAuthCredentialInventoryItem item = new OAuthCredentialInventoryItem(
            credentialId,
            "MAIL_ACCOUNT",
            ownerId,
            OAuthProviderType.GOOGLE,
            true,
            false,
            true,
            true,
            false,
            false,
            false,
            null,
            LocalDateTime.parse("2026-05-01T09:00:00"),
            LocalDateTime.parse("2026-05-07T10:00:00"),
            false,
            null
        );
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId))
            .thenReturn(Optional.of(new OAuthCredentialOwnerReference(credentialId, "MAIL_ACCOUNT", ownerId)));
        when(oauthCredentialRepository.findInventoryItemById(credentialId)).thenReturn(Optional.of(item));

        OAuthCredentialInventoryItem result = oauthCredentialAdminService.revokeProvider(credentialId);

        // Post-revoke state has no stored tokens; capability collapses to the
        // env-managed-secrets branch since this row carries a credentialKey.
        assertFalse(result.providerRevokeSupported());
        assertEquals(
            "Provider-side revoke requires a locally stored OAuth token; "
                + "this credential row only references env-managed secrets",
            result.providerRevokeUnsupportedReason()
        );
        verify(oauthCredentialService).revokeProviderTokens("MAIL_ACCOUNT", ownerId);
    }

    @Test
    @DisplayName("revokeProvider rejects unknown credential id")
    void revokeProviderRejectsUnknownCredentialId() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> oauthCredentialAdminService.revokeProvider(credentialId));
    }

    @Test
    @DisplayName("updateRevokeEndpoint stores trimmed HTTPS endpoint and returns refreshed capability")
    void updateRevokeEndpointStoresTrimmedHttpsEndpoint() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        OAuthCredential credential = new OAuthCredential();
        credential.setId(credentialId);
        credential.setOwnerType("MAIL_ACCOUNT");
        credential.setOwnerId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        credential.setProvider(OAuthProviderType.CUSTOM);
        OAuthCredentialInventoryItem item = inventory(credentialId, OAuthProviderType.CUSTOM, false, true, true);

        when(oauthCredentialRepository.findById(credentialId)).thenReturn(Optional.of(credential));
        when(oauthCredentialRepository.findInventoryItemById(credentialId)).thenReturn(Optional.of(item));
        when(oauthCredentialService.providerRevokeCapability("MAIL_ACCOUNT", credential.getOwnerId()))
            .thenReturn(new OAuthProviderRevokeCapability(true, null));

        OAuthCredentialInventoryItem result = oauthCredentialAdminService.updateRevokeEndpoint(
            credentialId,
            " https://custom.example/revoke "
        );

        assertEquals("https://custom.example/revoke", credential.getRevokeEndpoint());
        assertTrue(result.providerRevokeSupported());
        assertNull(result.providerRevokeUnsupportedReason());
        verify(oauthCredentialRepository).save(credential);
    }

    @Test
    @DisplayName("updateRevokeEndpoint clears blank endpoint")
    void updateRevokeEndpointClearsBlankEndpoint() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        OAuthCredential credential = new OAuthCredential();
        credential.setId(credentialId);
        credential.setOwnerType("MAIL_ACCOUNT");
        credential.setOwnerId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        credential.setProvider(OAuthProviderType.CUSTOM);
        credential.setRevokeEndpoint("https://custom.example/revoke");
        OAuthCredentialInventoryItem item = inventory(credentialId, OAuthProviderType.CUSTOM, false, true, true);

        when(oauthCredentialRepository.findById(credentialId)).thenReturn(Optional.of(credential));
        when(oauthCredentialRepository.findInventoryItemById(credentialId)).thenReturn(Optional.of(item));
        when(oauthCredentialService.providerRevokeCapability("MAIL_ACCOUNT", credential.getOwnerId()))
            .thenReturn(new OAuthProviderRevokeCapability(false, "Provider-side revoke endpoint is not configured for this CUSTOM credential"));

        OAuthCredentialInventoryItem result = oauthCredentialAdminService.updateRevokeEndpoint(credentialId, " ");

        assertNull(credential.getRevokeEndpoint());
        assertFalse(result.providerRevokeSupported());
        assertEquals(
            "Provider-side revoke endpoint is not configured for this CUSTOM credential",
            result.providerRevokeUnsupportedReason()
        );
        verify(oauthCredentialRepository).save(credential);
    }

    @Test
    @DisplayName("updateRevokeEndpoint rejects non-CUSTOM provider")
    void updateRevokeEndpointRejectsNonCustomProvider() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        OAuthCredential credential = new OAuthCredential();
        credential.setProvider(OAuthProviderType.GOOGLE);
        when(oauthCredentialRepository.findById(credentialId)).thenReturn(Optional.of(credential));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> oauthCredentialAdminService.updateRevokeEndpoint(credentialId, "https://custom.example/revoke")
        );

        assertEquals("Revoke endpoint can only be configured for CUSTOM OAuth credentials", ex.getMessage());
    }

    @Test
    @DisplayName("updateRevokeEndpoint rejects non-HTTPS endpoint")
    void updateRevokeEndpointRejectsNonHttpsEndpoint() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        OAuthCredential credential = new OAuthCredential();
        credential.setProvider(OAuthProviderType.CUSTOM);
        when(oauthCredentialRepository.findById(credentialId)).thenReturn(Optional.of(credential));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> oauthCredentialAdminService.updateRevokeEndpoint(credentialId, "http://custom.example/revoke")
        );

        assertEquals("Revoke endpoint must be a valid absolute HTTPS URL", ex.getMessage());
    }

    @Test
    @DisplayName("updateRevokeEndpoint rejects unknown credential id")
    void updateRevokeEndpointRejectsUnknownCredentialId() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialRepository.findById(credentialId)).thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> oauthCredentialAdminService.updateRevokeEndpoint(credentialId, "https://custom.example/revoke")
        );
    }

    @Test
    @DisplayName("getRevokeEndpointDetails returns persisted CUSTOM endpoint without token fields")
    void getRevokeEndpointDetailsReturnsPersistedCustomEndpoint() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OAuthCredential credential = new OAuthCredential();
        credential.setId(credentialId);
        credential.setOwnerType("MAIL_ACCOUNT");
        credential.setOwnerId(ownerId);
        credential.setProvider(OAuthProviderType.CUSTOM);
        credential.setRevokeEndpoint(" https://custom.example/revoke ");
        credential.setAccessToken("secret-access-token");
        credential.setRefreshToken("secret-refresh-token");
        when(oauthCredentialRepository.findById(credentialId)).thenReturn(Optional.of(credential));

        OAuthCredentialRevokeEndpointDetails details =
            oauthCredentialAdminService.getRevokeEndpointDetails(credentialId);

        assertEquals(credentialId, details.id());
        assertEquals("MAIL_ACCOUNT", details.ownerType());
        assertEquals(ownerId, details.ownerId());
        assertEquals(OAuthProviderType.CUSTOM, details.provider());
        assertTrue(details.revokeEndpointConfigured());
        assertEquals("https://custom.example/revoke", details.revokeEndpoint());
    }

    @Test
    @DisplayName("getRevokeEndpointDetails returns null endpoint when CUSTOM row is not configured")
    void getRevokeEndpointDetailsReturnsNullEndpointWhenNotConfigured() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        OAuthCredential credential = new OAuthCredential();
        credential.setId(credentialId);
        credential.setOwnerType("MAIL_ACCOUNT");
        credential.setOwnerId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        credential.setProvider(OAuthProviderType.CUSTOM);
        credential.setRevokeEndpoint(" ");
        when(oauthCredentialRepository.findById(credentialId)).thenReturn(Optional.of(credential));

        OAuthCredentialRevokeEndpointDetails details =
            oauthCredentialAdminService.getRevokeEndpointDetails(credentialId);

        assertFalse(details.revokeEndpointConfigured());
        assertNull(details.revokeEndpoint());
    }

    @Test
    @DisplayName("getRevokeEndpointDetails rejects non-CUSTOM provider")
    void getRevokeEndpointDetailsRejectsNonCustomProvider() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        OAuthCredential credential = new OAuthCredential();
        credential.setProvider(OAuthProviderType.GOOGLE);
        when(oauthCredentialRepository.findById(credentialId)).thenReturn(Optional.of(credential));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> oauthCredentialAdminService.getRevokeEndpointDetails(credentialId)
        );

        assertEquals("Revoke endpoint can only be read for CUSTOM OAuth credentials", ex.getMessage());
    }

    @Test
    @DisplayName("getRevokeEndpointDetails rejects unknown credential id")
    void getRevokeEndpointDetailsRejectsUnknownCredentialId() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialRepository.findById(credentialId)).thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> oauthCredentialAdminService.getRevokeEndpointDetails(credentialId)
        );
    }

    /**
     * Build an inventory row mirroring the repository projection: capability fields default
     * to {@code (false, null)} so each test path exercises the service-layer enrichment.
     */
    private static OAuthCredentialInventoryItem inventory(
        UUID id,
        OAuthProviderType provider,
        boolean credentialKeyConfigured,
        boolean accessTokenStored,
        boolean refreshTokenStored
    ) {
        boolean connected = accessTokenStored || refreshTokenStored;
        return new OAuthCredentialInventoryItem(
            id,
            "MAIL_ACCOUNT",
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
            provider,
            true,
            false,
            true,
            credentialKeyConfigured,
            accessTokenStored,
            refreshTokenStored,
            connected,
            connected ? LocalDateTime.parse("2026-05-06T12:00:00") : null,
            LocalDateTime.parse("2026-05-01T09:00:00"),
            LocalDateTime.parse("2026-05-06T11:00:00"),
            false,
            null
        );
    }
}
