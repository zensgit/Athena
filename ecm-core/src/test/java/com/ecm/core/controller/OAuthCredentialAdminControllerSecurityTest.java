package com.ecm.core.controller;

import com.ecm.core.integration.oauth.OAuthCredentialAdminService;
import com.ecm.core.integration.oauth.OAuthCredentialInventoryItem;
import com.ecm.core.integration.oauth.OAuthCredentialRevokeEndpointDetails;
import com.ecm.core.integration.oauth.OAuthReauthRequiredException;
import com.ecm.core.integration.oauth.OAuthProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OAuthCredentialAdminController.class)
@ContextConfiguration(classes = {
    OAuthCredentialAdminController.class,
    RestExceptionHandler.class,
    OAuthCredentialAdminControllerSecurityTest.TestSecurityConfig.class
})
class OAuthCredentialAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuthCredentialAdminService oauthCredentialAdminService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});
            return http.build();
        }
    }

    @Test
    @DisplayName("anonymous OAuth credential inventory request returns 401")
    void anonymousInventoryReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/oauth-credentials"))
            .andExpect(status().isUnauthorized());
        // Provider revoke must also reject anonymous callers; keep parity with inventory access.
        mockMvc.perform(post("/api/v1/admin/oauth-credentials/11111111-2222-3333-4444-555555555555/revoke"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/admin/oauth-credentials/11111111-2222-3333-4444-555555555555/revoke-endpoint")
                .contentType(APPLICATION_JSON)
                .content("{\"revokeEndpoint\":\"https://custom.example/revoke\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/oauth-credentials/11111111-2222-3333-4444-555555555555/revoke-endpoint"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("OAuth credential inventory requires admin role")
    void inventoryRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/oauth-credentials"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/oauth-credentials/11111111-2222-3333-4444-555555555555/require-reauth"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/oauth-credentials/11111111-2222-3333-4444-555555555555/refresh-now"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/oauth-credentials/11111111-2222-3333-4444-555555555555/revoke"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/oauth-credentials/11111111-2222-3333-4444-555555555555/revoke-endpoint")
                .contentType(APPLICATION_JSON)
                .content("{\"revokeEndpoint\":\"https://custom.example/revoke\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/oauth-credentials/11111111-2222-3333-4444-555555555555/revoke-endpoint"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can list redacted OAuth credential inventory")
    void adminCanListRedactedInventory() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(oauthCredentialAdminService.listCredentials("MAIL_ACCOUNT", OAuthProviderType.GOOGLE)).thenReturn(List.of(
            new OAuthCredentialInventoryItem(
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
                LocalDateTime.parse("2026-05-06T10:15:30"),
                LocalDateTime.parse("2026-05-01T09:00:00"),
                LocalDateTime.parse("2026-05-06T10:00:00"),
                true,
                null
            )
        ));

        mockMvc.perform(get("/api/v1/admin/oauth-credentials")
                .param("ownerType", "MAIL_ACCOUNT")
                .param("provider", "GOOGLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is(credentialId.toString())))
            .andExpect(jsonPath("$[0].ownerType", is("MAIL_ACCOUNT")))
            .andExpect(jsonPath("$[0].ownerId", is(ownerId.toString())))
            .andExpect(jsonPath("$[0].provider", is("GOOGLE")))
            .andExpect(jsonPath("$[0].tokenEndpointConfigured", is(true)))
            .andExpect(jsonPath("$[0].revokeEndpointConfigured", is(false)))
            .andExpect(jsonPath("$[0].scopeConfigured", is(true)))
            .andExpect(jsonPath("$[0].credentialKeyConfigured", is(true)))
            .andExpect(jsonPath("$[0].accessTokenStored", is(true)))
            .andExpect(jsonPath("$[0].refreshTokenStored", is(true)))
            .andExpect(jsonPath("$[0].connected", is(true)))
            // Provider-revoke capability metadata: the frontend keys the Provider Revoke button
            // off these two fields instead of hard-coding `provider === 'GOOGLE'`.
            .andExpect(jsonPath("$[0].providerRevokeSupported", is(true)))
            .andExpect(jsonPath("$[0].providerRevokeUnsupportedReason").doesNotExist())
            .andExpect(jsonPath("$[0].accessToken").doesNotExist())
            .andExpect(jsonPath("$[0].refreshToken").doesNotExist());

        verify(oauthCredentialAdminService).listCredentials("MAIL_ACCOUNT", OAuthProviderType.GOOGLE);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can require OAuth reauthorization without token disclosure")
    void adminCanRequireReauth() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(oauthCredentialAdminService.requireReauth(credentialId)).thenReturn(
            new OAuthCredentialInventoryItem(
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
                "Provider-side revoke requires a locally stored OAuth token; "
                    + "this credential row only references env-managed secrets"
            )
        );

        mockMvc.perform(post("/api/v1/admin/oauth-credentials/{credentialId}/require-reauth", credentialId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(credentialId.toString())))
            .andExpect(jsonPath("$.connected", is(false)))
            .andExpect(jsonPath("$.accessTokenStored", is(false)))
            .andExpect(jsonPath("$.refreshTokenStored", is(false)))
            .andExpect(jsonPath("$.providerRevokeSupported", is(false)))
            .andExpect(jsonPath(
                "$.providerRevokeUnsupportedReason",
                is("Provider-side revoke requires a locally stored OAuth token; "
                    + "this credential row only references env-managed secrets")
            ))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(oauthCredentialAdminService).requireReauth(credentialId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can refresh OAuth credential without token disclosure")
    void adminCanRefreshNow() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(oauthCredentialAdminService.refreshNow(credentialId)).thenReturn(
            new OAuthCredentialInventoryItem(
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
                true,
                null
            )
        );

        mockMvc.perform(post("/api/v1/admin/oauth-credentials/{credentialId}/refresh-now", credentialId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(credentialId.toString())))
            .andExpect(jsonPath("$.connected", is(true)))
            .andExpect(jsonPath("$.accessTokenStored", is(true)))
            .andExpect(jsonPath("$.refreshTokenStored", is(true)))
            .andExpect(jsonPath("$.providerRevokeSupported", is(true)))
            .andExpect(jsonPath("$.providerRevokeUnsupportedReason").doesNotExist())
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(oauthCredentialAdminService).refreshNow(credentialId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("refresh OAuth credential reports reauth-required as conflict")
    void refreshNowReportsReauthRequiredAsConflict() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(oauthCredentialAdminService.refreshNow(credentialId)).thenThrow(
            new OAuthReauthRequiredException("MAIL_ACCOUNT", ownerId, "invalid_grant", "Token expired")
        );

        mockMvc.perform(post("/api/v1/admin/oauth-credentials/{credentialId}/refresh-now", credentialId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", is("OAUTH_REAUTH_REQUIRED: invalid_grant - Token expired")));

        verify(oauthCredentialAdminService).refreshNow(credentialId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can revoke OAuth credential at provider without token disclosure")
    void adminCanRevokeProviderTokens() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(oauthCredentialAdminService.revokeProvider(credentialId)).thenReturn(
            new OAuthCredentialInventoryItem(
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
                "Provider-side revoke requires a locally stored OAuth token; "
                    + "this credential row only references env-managed secrets"
            )
        );

        mockMvc.perform(post("/api/v1/admin/oauth-credentials/{credentialId}/revoke", credentialId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(credentialId.toString())))
            .andExpect(jsonPath("$.connected", is(false)))
            .andExpect(jsonPath("$.accessTokenStored", is(false)))
            .andExpect(jsonPath("$.refreshTokenStored", is(false)))
            .andExpect(jsonPath("$.providerRevokeSupported", is(false)))
            .andExpect(jsonPath(
                "$.providerRevokeUnsupportedReason",
                is("Provider-side revoke requires a locally stored OAuth token; "
                    + "this credential row only references env-managed secrets")
            ))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(oauthCredentialAdminService).revokeProvider(credentialId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("revoke OAuth credential reports unsupported provider as 400")
    void revokeReportsUnsupportedProviderAs400() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialAdminService.revokeProvider(credentialId)).thenThrow(
            new IllegalArgumentException("Provider-side revoke is not yet supported for MICROSOFT")
        );

        mockMvc.perform(post("/api/v1/admin/oauth-credentials/{credentialId}/revoke", credentialId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(
                "$.message",
                is("Provider-side revoke is not yet supported for MICROSOFT")
            ));

        verify(oauthCredentialAdminService).revokeProvider(credentialId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("revoke OAuth credential surfaces provider failure as 500 with diagnostic message")
    void revokeReportsProviderFailureAs500() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialAdminService.revokeProvider(credentialId)).thenThrow(
            new IllegalStateException("OAuth token revoke failed for owner gmail: provider returned 503")
        );

        mockMvc.perform(post("/api/v1/admin/oauth-credentials/{credentialId}/revoke", credentialId))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath(
                "$.message",
                is("OAuth token revoke failed for owner gmail: provider returned 503")
            ));

        verify(oauthCredentialAdminService).revokeProvider(credentialId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can read CUSTOM revoke endpoint details without token disclosure")
    void adminCanReadCustomRevokeEndpointDetails() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(oauthCredentialAdminService.getRevokeEndpointDetails(credentialId)).thenReturn(
            new OAuthCredentialRevokeEndpointDetails(
                credentialId,
                "MAIL_ACCOUNT",
                ownerId,
                OAuthProviderType.CUSTOM,
                true,
                "https://custom.example/revoke"
            )
        );

        mockMvc.perform(get("/api/v1/admin/oauth-credentials/{credentialId}/revoke-endpoint", credentialId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(credentialId.toString())))
            .andExpect(jsonPath("$.ownerType", is("MAIL_ACCOUNT")))
            .andExpect(jsonPath("$.ownerId", is(ownerId.toString())))
            .andExpect(jsonPath("$.provider", is("CUSTOM")))
            .andExpect(jsonPath("$.revokeEndpointConfigured", is(true)))
            .andExpect(jsonPath("$.revokeEndpoint", is("https://custom.example/revoke")))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(oauthCredentialAdminService).getRevokeEndpointDetails(credentialId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("read revoke endpoint reports provider mismatch as 400")
    void readRevokeEndpointReportsProviderMismatchAs400() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialAdminService.getRevokeEndpointDetails(credentialId)).thenThrow(
            new IllegalArgumentException("Revoke endpoint can only be read for CUSTOM OAuth credentials")
        );

        mockMvc.perform(get("/api/v1/admin/oauth-credentials/{credentialId}/revoke-endpoint", credentialId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(
                "$.message",
                is("Revoke endpoint can only be read for CUSTOM OAuth credentials")
            ));

        verify(oauthCredentialAdminService).getRevokeEndpointDetails(credentialId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can configure CUSTOM revoke endpoint without token disclosure")
    void adminCanConfigureCustomRevokeEndpoint() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(oauthCredentialAdminService.updateRevokeEndpoint(credentialId, "https://custom.example/revoke")).thenReturn(
            new OAuthCredentialInventoryItem(
                credentialId,
                "MAIL_ACCOUNT",
                ownerId,
                OAuthProviderType.CUSTOM,
                true,
                true,
                false,
                true,
                false,
                true,
                true,
                true,
                LocalDateTime.parse("2026-05-06T12:00:00"),
                LocalDateTime.parse("2026-05-01T09:00:00"),
                LocalDateTime.parse("2026-05-11T10:00:00"),
                true,
                null
            )
        );

        mockMvc.perform(put("/api/v1/admin/oauth-credentials/{credentialId}/revoke-endpoint", credentialId)
                .contentType(APPLICATION_JSON)
                .content("{\"revokeEndpoint\":\"https://custom.example/revoke\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(credentialId.toString())))
            .andExpect(jsonPath("$.provider", is("CUSTOM")))
            .andExpect(jsonPath("$.revokeEndpointConfigured", is(true)))
            .andExpect(jsonPath("$.providerRevokeSupported", is(true)))
            .andExpect(jsonPath("$.providerRevokeUnsupportedReason").doesNotExist())
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(oauthCredentialAdminService).updateRevokeEndpoint(credentialId, "https://custom.example/revoke");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("configure revoke endpoint reports provider mismatch as 400")
    void configureRevokeEndpointReportsProviderMismatchAs400() throws Exception {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialAdminService.updateRevokeEndpoint(credentialId, "https://custom.example/revoke")).thenThrow(
            new IllegalArgumentException("Revoke endpoint can only be configured for CUSTOM OAuth credentials")
        );

        mockMvc.perform(put("/api/v1/admin/oauth-credentials/{credentialId}/revoke-endpoint", credentialId)
                .contentType(APPLICATION_JSON)
                .content("{\"revokeEndpoint\":\"https://custom.example/revoke\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(
                "$.message",
                is("Revoke endpoint can only be configured for CUSTOM OAuth credentials")
            ));

        verify(oauthCredentialAdminService).updateRevokeEndpoint(credentialId, "https://custom.example/revoke");
    }
}
