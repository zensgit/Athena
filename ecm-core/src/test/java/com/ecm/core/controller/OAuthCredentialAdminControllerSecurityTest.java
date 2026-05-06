package com.ecm.core.controller;

import com.ecm.core.integration.oauth.OAuthCredentialAdminService;
import com.ecm.core.integration.oauth.OAuthCredentialInventoryItem;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OAuthCredentialAdminController.class)
@ContextConfiguration(classes = {
    OAuthCredentialAdminController.class,
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
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("OAuth credential inventory requires admin role")
    void inventoryRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/oauth-credentials"))
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
                LocalDateTime.parse("2026-05-06T10:00:00")
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
            .andExpect(jsonPath("$[0].scopeConfigured", is(true)))
            .andExpect(jsonPath("$[0].credentialKeyConfigured", is(true)))
            .andExpect(jsonPath("$[0].accessTokenStored", is(true)))
            .andExpect(jsonPath("$[0].refreshTokenStored", is(true)))
            .andExpect(jsonPath("$[0].connected", is(true)))
            .andExpect(jsonPath("$[0].accessToken").doesNotExist())
            .andExpect(jsonPath("$[0].refreshToken").doesNotExist());

        verify(oauthCredentialAdminService).listCredentials("MAIL_ACCOUNT", OAuthProviderType.GOOGLE);
    }
}
