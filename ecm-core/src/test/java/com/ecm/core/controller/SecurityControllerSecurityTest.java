package com.ecm.core.controller;

import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link SecurityController}.
 *
 * SecurityController exposes per-node ACL operations and the current-user
 * info endpoint. NO controller-level @PreAuthorize annotations — every
 * endpoint is gated only by isAuthenticated(). Per-node permission checks
 * (does this user have CHANGE_PERMISSIONS on this node?) are enforced
 * inside SecurityService / NodeService.
 *
 * The unauth-401 cases are load-bearing: a missing filter rule on
 * /api/v1/security/** would let anonymous callers read AND modify ACLs
 * across the entire repository in a single endpoint.
 */
@WebMvcTest(controllers = SecurityController.class)
@ContextConfiguration(classes = {
    SecurityController.class,
    RestExceptionHandler.class,
    SecurityControllerSecurityTest.TestSecurityConfig.class
})
class SecurityControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SecurityService securityService;

    @MockBean
    private NodeService nodeService;

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

    private static final String BASE = "/api/v1/security";

    // ====== unauth → 401 (sampled across reads + ACL writes) ======

    @Test
    @DisplayName("unauthenticated GET /security/nodes/{id}/permissions returns 401")
    void unauthenticatedGetPermissionsReturns401() throws Exception {
        mockMvc.perform(get(BASE + "/nodes/{id}/permissions", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /security/nodes/{id}/effective-permissions returns 401")
    void unauthenticatedGetEffectivePermissionsReturns401() throws Exception {
        mockMvc.perform(get(BASE + "/nodes/{id}/effective-permissions", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /security/nodes/{id}/permissions returns 401 — load-bearing ACL-write endpoint")
    void unauthenticatedSetPermissionsReturns401() throws Exception {
        // Setting permissions changes who can read/write a node. A missing rule
        // would let any anonymous caller mutate ACLs.
        mockMvc.perform(post(BASE + "/nodes/{id}/permissions", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /security/nodes/{id}/permissions returns 401")
    void unauthenticatedClearPermissionsReturns401() throws Exception {
        mockMvc.perform(delete(BASE + "/nodes/{id}/permissions", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /security/nodes/{id}/take-ownership returns 401")
    void unauthenticatedTakeOwnershipReturns401() throws Exception {
        mockMvc.perform(post(BASE + "/nodes/{id}/take-ownership", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /security/nodes/{id}/inherit-permissions returns 401")
    void unauthenticatedInheritPermissionsReturns401() throws Exception {
        mockMvc.perform(post(BASE + "/nodes/{id}/inherit-permissions", UUID.randomUUID())
                .param("inherit", "true"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /security/users/current returns 401")
    void unauthenticatedGetCurrentUserReturns401() throws Exception {
        mockMvc.perform(get(BASE + "/users/current"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /security/permissions/cleanup-expired returns 401")
    void unauthenticatedCleanupExpiredReturns401() throws Exception {
        mockMvc.perform(post(BASE + "/permissions/cleanup-expired"))
            .andExpect(status().isUnauthorized());
    }

    // ====== ROLE_USER → 200 on read (gate is isAuthenticated only) ======

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    @DisplayName("ROLE_USER can read current-user authorities (isAuthenticated only)")
    void userCanReadCurrentUserAuthorities() throws Exception {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.getUserAuthorities("alice")).thenReturn(java.util.Set.of("ROLE_USER"));

        mockMvc.perform(get(BASE + "/users/current/authorities"))
            .andExpect(status().isOk());
    }
}
