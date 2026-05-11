package com.ecm.core.integration.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class OAuthCredentialServiceTest {

    private static final String OWNER_TYPE = "MAIL_ACCOUNT";

    @Mock
    private Environment environment;

    @Mock
    private OAuthCredentialOwnerAdapter adapter;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OAuthCredentialService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        service = new OAuthCredentialService(environment, restTemplate, List.of(adapter));
        when(adapter.ownerType()).thenReturn(OWNER_TYPE);
    }

    @Test
    @DisplayName("Environment check uses adapter credential-key env naming")
    void checkEnvironmentUsesCredentialKeyEnvNaming() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            null,
            "gmail-joshua",
            null,
            null,
            null
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);
        when(adapter.buildCredentialEnvKey("GMAIL_JOSHUA", "CLIENT_ID")).thenReturn("ECM_MAIL_OAUTH_GMAIL_JOSHUA_CLIENT_ID");
        when(adapter.buildCredentialEnvKey("GMAIL_JOSHUA", "REFRESH_TOKEN")).thenReturn("ECM_MAIL_OAUTH_GMAIL_JOSHUA_REFRESH_TOKEN");
        when(environment.getProperty("ECM_MAIL_OAUTH_GMAIL_JOSHUA_CLIENT_ID")).thenReturn("client");
        when(environment.getProperty("ECM_MAIL_OAUTH_GMAIL_JOSHUA_REFRESH_TOKEN")).thenReturn("");

        OAuthEnvironmentStatus result = service.checkEnvironment(OWNER_TYPE, ownerId);

        assertTrue(result.oauthAccount());
        assertEquals("GMAIL_JOSHUA", result.credentialKey());
        assertEquals(List.of("ECM_MAIL_OAUTH_GMAIL_JOSHUA_REFRESH_TOKEN"), result.missingEnvKeys());
    }

    @Test
    @DisplayName("resolveAccessToken refreshes token through the generic owner adapter")
    void resolveAccessTokenRefreshesTokenThroughAdapter() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            "https://mail.google.com/",
            null,
            null,
            "refresh-token",
            null
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "CLIENT_ID")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID");
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "CLIENT_SECRET")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET");
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "SCOPE")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_SCOPE");
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "TOKEN_ENDPOINT")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_TOKEN_ENDPOINT");
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID")).thenReturn("client-id");
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET")).thenReturn("client-secret");
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_SCOPE")).thenReturn(null);
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_TOKEN_ENDPOINT")).thenReturn(null);

        server.expect(requestTo("https://oauth2.googleapis.com/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=refresh-token")))
            .andRespond(withSuccess("""
                {"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}
                """, MediaType.APPLICATION_JSON));

        String token = service.resolveAccessToken(OWNER_TYPE, ownerId);

        assertEquals("new-access", token);
        verify(adapter).saveTokens(eq(ownerId), eq("new-access"), eq("new-refresh"), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        server.verify();
    }

    @Test
    @DisplayName("refreshAccessTokenNow always performs provider refresh through adapter")
    void refreshAccessTokenNowAlwaysPerformsProviderRefreshThroughAdapter() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            "https://mail.google.com/",
            null,
            "old-access",
            "refresh-token",
            LocalDateTime.now().plusHours(1)
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "CLIENT_ID")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID");
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "CLIENT_SECRET")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET");
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "SCOPE")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_SCOPE");
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "TOKEN_ENDPOINT")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_TOKEN_ENDPOINT");
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID")).thenReturn("client-id");
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET")).thenReturn("client-secret");
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_SCOPE")).thenReturn(null);
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_TOKEN_ENDPOINT")).thenReturn(null);
        when(adapter.saveTokens(
            eq(ownerId),
            eq("forced-access"),
            eq("forced-refresh"),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            "https://mail.google.com/",
            null,
            "forced-access",
            "forced-refresh",
            LocalDateTime.now().plusHours(1)
        ));

        server.expect(requestTo("https://oauth2.googleapis.com/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=refresh-token")))
            .andRespond(withSuccess("""
                {"access_token":"forced-access","refresh_token":"forced-refresh","expires_in":3600}
                """, MediaType.APPLICATION_JSON));

        String token = service.refreshAccessTokenNow(OWNER_TYPE, ownerId);

        assertEquals("forced-access", token);
        verify(adapter).saveTokens(
            eq(ownerId),
            eq("forced-access"),
            eq("forced-refresh"),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
        server.verify();
    }

    @Test
    @DisplayName("revokeProviderTokens prefers refresh token and clears local state on 200")
    void revokeProviderTokensPrefersRefreshTokenAndClearsLocalStateOn200() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            null,
            null,
            "stored-access",
            "stored-refresh",
            LocalDateTime.now().plusHours(1)
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);

        server.expect(requestTo("https://oauth2.googleapis.com/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("token=stored-refresh")))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        service.revokeProviderTokens(OWNER_TYPE, ownerId);

        verify(adapter).clearTokens(ownerId);
        server.verify();
    }

    @Test
    @DisplayName("revokeProviderTokens falls back to access token when no refresh token stored")
    void revokeProviderTokensFallsBackToAccessTokenWhenNoRefreshTokenStored() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            null,
            null,
            "only-access",
            null,
            LocalDateTime.now().plusHours(1)
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);

        server.expect(requestTo("https://oauth2.googleapis.com/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("token=only-access")))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        service.revokeProviderTokens(OWNER_TYPE, ownerId);

        verify(adapter).clearTokens(ownerId);
        server.verify();
    }

    @Test
    @DisplayName("revokeProviderTokens treats invalid_token as success-equivalent and clears local state")
    void revokeProviderTokensTreatsInvalidTokenAsSuccessEquivalent() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            null,
            null,
            null,
            "already-revoked",
            null
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);

        server.expect(requestTo("https://oauth2.googleapis.com/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withBadRequest()
                .body("{\"error\":\"invalid_token\"}")
                .contentType(MediaType.APPLICATION_JSON));

        service.revokeProviderTokens(OWNER_TYPE, ownerId);

        verify(adapter).clearTokens(ownerId);
        server.verify();
    }

    @Test
    @DisplayName("revokeProviderTokens preserves local state and throws when provider returns 5xx")
    void revokeProviderTokensPreservesLocalStateOnProviderServerError() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            null,
            null,
            null,
            "stored-refresh",
            null
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);

        server.expect(requestTo("https://oauth2.googleapis.com/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.revokeProviderTokens(OWNER_TYPE, ownerId)
        );
        assertTrue(ex.getMessage().contains("revoke failed"), () -> "unexpected message: " + ex.getMessage());
        verify(adapter, never()).clearTokens(any());
        server.verify();
    }

    @Test
    @DisplayName("revokeProviderTokens rejects Microsoft because per-token revoke is not supported")
    void revokeProviderTokensRejectsMicrosoftProvider() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "outlook",
            OAuthProviderType.MICROSOFT,
            null,
            null,
            null,
            null,
            "access",
            "refresh",
            null
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.revokeProviderTokens(OWNER_TYPE, ownerId)
        );
        assertEquals("Provider-side revoke is not yet supported for MICROSOFT", ex.getMessage());
        verify(adapter, never()).clearTokens(any());
    }

    @Test
    @DisplayName("revokeProviderTokens uses configured CUSTOM revoke endpoint")
    void revokeProviderTokensUsesCustomRevokeEndpoint() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "custom",
            OAuthProviderType.CUSTOM,
            "https://custom.example/token",
            "https://custom.example/revoke",
            null,
            null,
            null,
            "stored-access",
            "stored-refresh",
            LocalDateTime.now().plusHours(1)
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);

        server.expect(requestTo("https://custom.example/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("token=stored-refresh")))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        service.revokeProviderTokens(OWNER_TYPE, ownerId);

        verify(adapter).clearTokens(ownerId);
        server.verify();
    }

    @Test
    @DisplayName("revokeProviderTokens uses CUSTOM credential-key env revoke endpoint fallback")
    void revokeProviderTokensUsesCustomCredentialKeyEnvRevokeEndpointFallback() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "custom",
            OAuthProviderType.CUSTOM,
            "https://custom.example/token",
            null,
            null,
            null,
            "vendor-one",
            "stored-access",
            "stored-refresh",
            LocalDateTime.now().plusHours(1)
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);
        when(adapter.buildCredentialEnvKey("VENDOR_ONE", "REVOKE_ENDPOINT"))
            .thenReturn("ECM_MAIL_OAUTH_VENDOR_ONE_REVOKE_ENDPOINT");
        when(environment.getProperty("ECM_MAIL_OAUTH_VENDOR_ONE_REVOKE_ENDPOINT"))
            .thenReturn("https://env.example/revoke");

        server.expect(requestTo("https://env.example/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("token=stored-refresh")))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        service.revokeProviderTokens(OWNER_TYPE, ownerId);

        verify(adapter).clearTokens(ownerId);
        server.verify();
    }

    @Test
    @DisplayName("revokeProviderTokens rejects CUSTOM without configured revoke endpoint")
    void revokeProviderTokensRejectsCustomWithoutRevokeEndpoint() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "custom",
            OAuthProviderType.CUSTOM,
            "https://custom.example/token",
            null,
            null,
            null,
            null,
            "stored-access",
            "stored-refresh",
            LocalDateTime.now().plusHours(1)
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.revokeProviderTokens(OWNER_TYPE, ownerId)
        );
        assertEquals("Provider-side revoke endpoint is not configured for this CUSTOM credential", ex.getMessage());
        verify(adapter, never()).clearTokens(any());
    }

    @Test
    @DisplayName("revokeProviderTokens rejects env-managed credential-key-only rows as unsupported")
    void revokeProviderTokensRejectsEnvManagedCredentialKeyOnlyRows() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            null,
            "gmail-joshua",
            null,
            null,
            null
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.revokeProviderTokens(OWNER_TYPE, ownerId)
        );
        assertTrue(ex.getMessage().contains("env-managed"), () -> "unexpected message: " + ex.getMessage());
        verify(adapter, never()).clearTokens(any());
    }

    @Test
    @DisplayName("revokeProviderTokens rejects rows with no stored tokens at all")
    void revokeProviderTokensRejectsRowsWithNoStoredTokens() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.revokeProviderTokens(OWNER_TYPE, ownerId)
        );
        assertTrue(ex.getMessage().contains("No locally stored OAuth token"), () -> "unexpected message: " + ex.getMessage());
        verify(adapter, never()).clearTokens(any());
    }

    @Test
    @DisplayName("invalid_grant clears tokens and raises reauth-required")
    void invalidGrantClearsTokensAndRaisesReauthRequired() {
        UUID ownerId = UUID.randomUUID();
        OAuthCredentialOwner owner = new OAuthCredentialOwner(
            OWNER_TYPE,
            ownerId,
            "gmail",
            OAuthProviderType.GOOGLE,
            null,
            null,
            null,
            null,
            null,
            "refresh-token",
            null
        );
        when(adapter.loadOwner(ownerId)).thenReturn(owner);
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "CLIENT_ID")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID");
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "CLIENT_SECRET")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET");
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "SCOPE")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_SCOPE");
        when(adapter.buildProviderEnvKey(OAuthProviderType.GOOGLE, "TOKEN_ENDPOINT")).thenReturn("ECM_MAIL_OAUTH_GOOGLE_TOKEN_ENDPOINT");
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID")).thenReturn("client-id");
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET")).thenReturn("client-secret");
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_SCOPE")).thenReturn(null);
        when(environment.getProperty("ECM_MAIL_OAUTH_GOOGLE_TOKEN_ENDPOINT")).thenReturn(null);

        server.expect(requestTo("https://oauth2.googleapis.com/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withBadRequest().body("""
                {"error":"invalid_grant","error_description":"Token expired"}
                """).contentType(MediaType.APPLICATION_JSON));

        OAuthReauthRequiredException exception = assertThrows(
            OAuthReauthRequiredException.class,
            () -> service.resolveAccessToken(OWNER_TYPE, ownerId)
        );

        assertEquals("invalid_grant", exception.getError());
        verify(adapter).clearTokens(ownerId);
        server.verify();
    }
}
