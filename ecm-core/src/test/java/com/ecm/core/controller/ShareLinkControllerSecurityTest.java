package com.ecm.core.controller;

import com.ecm.core.service.ShareLinkService;
import com.ecm.core.service.ShareLinkService.ShareLinkAccessResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link ShareLinkController}.
 *
 * Two authorization tiers, mirroring the production SecurityConfig:
 *   - PUBLIC redeem: /api/share/access/** and /api/v1/share/access/** are
 *     permitAll() because external recipients of a share link must be able
 *     to redeem the token without an account. ShareLinkService is responsible
 *     for token validation, password checks, IP allowlist, etc.
 *   - everything else: isAuthenticated() (controller has no @PreAuthorize so
 *     additional ownership/role enforcement is service-side, not asserted here).
 *
 * The test config explicitly mirrors the production permitAll() rule so the
 * test fails if a future change accidentally locks down the redeem flow.
 */
@WebMvcTest(controllers = ShareLinkController.class)
@ContextConfiguration(classes = {
    ShareLinkController.class,
    RestExceptionHandler.class,
    ShareLinkControllerSecurityTest.TestSecurityConfig.class
})
class ShareLinkControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShareLinkService shareLinkService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    // mirrors prod SecurityConfig.java line 52 — share-link redeem is public
                    .requestMatchers("/api/v1/share/access/**", "/api/share/access/**").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});
            return http.build();
        }
    }

    // ====== authenticated-required endpoints ======

    @Test
    @DisplayName("unauthenticated POST /share/nodes/{nodeId} returns 401")
    void unauthenticatedCreateShareLinkReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/share/nodes/{nodeId}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /share/{token} returns 401")
    void unauthenticatedGetShareLinkReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/share/{token}", "abc123"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /share/my returns 401")
    void unauthenticatedGetMyShareLinksReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/share/my"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated PUT /share/{token} returns 401")
    void unauthenticatedUpdateShareLinkReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/share/{token}", "abc123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /share/{token} returns 401")
    void unauthenticatedDeleteShareLinkReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/share/{token}", "abc123"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /share/{token}/deactivate returns 401")
    void unauthenticatedDeactivateReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/share/{token}/deactivate", "abc123"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /share/admin/all returns 401")
    void unauthenticatedListAllReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/share/admin/all"))
            .andExpect(status().isUnauthorized());
    }

    // ====== public redeem path — must NOT require authentication ======

    @Test
    @DisplayName("unauthenticated GET /share/access/{token} reaches the controller (NOT 401) — public redeem flow")
    void unauthenticatedRedeemReachesController() throws Exception {
        // Service returns an invalid result; controller maps it to 403, NOT Spring Security to 401.
        // The point of this test is the path is permitAll() — the resulting 403 proves the request
        // got past the filter chain to the controller body, where ShareLinkService.accessShareLink
        // ran and returned its own auth-style decision.
        when(shareLinkService.accessShareLink(any(), any(), any()))
            .thenReturn(ShareLinkAccessResult.invalid("token-not-found"));

        mockMvc.perform(get("/api/v1/share/access/{token}", "fake-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("unauthenticated GET /share/access/{token} also reaches the controller on /api/share legacy prefix")
    void unauthenticatedRedeemAlsoOnLegacyPrefix() throws Exception {
        when(shareLinkService.accessShareLink(any(), any(), any()))
            .thenReturn(ShareLinkAccessResult.invalid("token-not-found"));

        mockMvc.perform(get("/api/share/access/{token}", "fake-token"))
            .andExpect(status().isForbidden());
    }

}
