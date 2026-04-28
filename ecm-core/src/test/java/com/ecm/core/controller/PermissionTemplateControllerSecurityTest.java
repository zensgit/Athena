package com.ecm.core.controller;

import com.ecm.core.service.AuditService;
import com.ecm.core.service.PermissionTemplateService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link PermissionTemplateController}.
 *
 * Every endpoint is gated by @PreAuthorize("hasRole('ADMIN')"). Permission
 * templates govern other security decisions (they're applied to nodes via
 * /apply, defining the ACL bundle that shapes who can read/write the target).
 * A widening of this gate would let editors (or anyone authenticated)
 * redefine the security templates that everything else inherits from —
 * that's why this is part of the "control plane" round.
 *
 * Three load-bearing assertions:
 *   - unauth → 401 on every endpoint
 *   - ROLE_USER → 403 on a representative write proves @PreAuthorize fires
 *   - ROLE_EDITOR → 403 on /apply proves the gate is admin-only and NOT
 *     widened to admin-or-editor
 */
@WebMvcTest(controllers = PermissionTemplateController.class)
@ContextConfiguration(classes = {
    PermissionTemplateController.class,
    RestExceptionHandler.class,
    PermissionTemplateControllerSecurityTest.TestSecurityConfig.class
})
class PermissionTemplateControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PermissionTemplateService permissionTemplateService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private SecurityService securityService;

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

    private static final String BASE = "/api/v1/security/permission-templates";

    // ====== unauth → 401 ======

    @Test
    @DisplayName("unauthenticated GET /permission-templates returns 401")
    void unauthenticatedListReturns401() throws Exception {
        mockMvc.perform(get(BASE))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /permission-templates returns 401")
    void unauthenticatedCreateReturns401() throws Exception {
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated PUT /permission-templates/{id} returns 401")
    void unauthenticatedUpdateReturns401() throws Exception {
        mockMvc.perform(put(BASE + "/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /permission-templates/{id} returns 401")
    void unauthenticatedDeleteReturns401() throws Exception {
        mockMvc.perform(delete(BASE + "/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /permission-templates/{id}/apply returns 401 — load-bearing control-plane endpoint")
    void unauthenticatedApplyReturns401() throws Exception {
        // Applying a permission template overwrites a node's ACLs in one call.
        // A missing rule would let any anonymous caller take over node permissions.
        mockMvc.perform(post(BASE + "/{id}/apply", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /permission-templates/{id}/versions returns 401")
    void unauthenticatedVersionsReturns401() throws Exception {
        mockMvc.perform(get(BASE + "/{id}/versions", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /permission-templates/{id}/versions/{vid}/rollback returns 401")
    void unauthenticatedRollbackReturns401() throws Exception {
        mockMvc.perform(post(BASE + "/{id}/versions/{vid}/rollback", UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    // ====== ROLE_USER → 403 ======

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot list permission templates (admin-only gate even on read)")
    void userCannotListTemplates() throws Exception {
        mockMvc.perform(get(BASE))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot apply permission template")
    void userCannotApplyTemplate() throws Exception {
        mockMvc.perform(post(BASE + "/{id}/apply", UUID.randomUUID())
                .param("nodeId", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    // ====== ROLE_EDITOR → 403 (load-bearing) ======

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("ROLE_EDITOR cannot apply permission template — admin-only, EDITOR not enough")
    void editorCannotApplyTemplate() throws Exception {
        // Load-bearing: if a future change widens the gate to
        // hasAnyRole('ADMIN','EDITOR'), this test flips from 403 to 200.
        mockMvc.perform(post(BASE + "/{id}/apply", UUID.randomUUID())
                .param("nodeId", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    // ====== ROLE_ADMIN → 200 (gate admits admin) ======

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN can list permission templates")
    void adminCanListTemplates() throws Exception {
        when(permissionTemplateService.list()).thenReturn(List.of());

        mockMvc.perform(get(BASE))
            .andExpect(status().isOk());
    }
}
