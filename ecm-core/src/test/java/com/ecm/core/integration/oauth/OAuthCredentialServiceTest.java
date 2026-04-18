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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
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
