package com.ecm.core.controller;

import com.ecm.core.cmis.CmisAtomPubSerializer;
import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisContentVersioningService;
import com.ecm.core.cmis.CmisModels;
import com.ecm.core.cmis.CmisMutationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security test for {@link CmisAtomPubController}.
 *
 * Unlike the Transfer/WOPI thread, CMIS does NOT sit on a {@code permitAll()}
 * exception. Production {@code SecurityConfig.java:60} routes
 * {@code /api/cmis/atom/**} (and {@code /api/v1/cmis/atom/**}) under
 * {@code requestMatchers("/api/**").authenticated()} — i.e. JWT bearer auth
 * is required, like every other {@code /api/**} endpoint.
 *
 * <p>This test therefore looks more like the legacy {@code @WithMockUser}
 * backfill than the protocol-token tests for Transfer/WOPI. The CMIS-specific
 * concerns it documents:
 *
 * <ol>
 *   <li>Both URL prefixes ({@code /api/cmis/atom} and {@code /api/v1/cmis/atom})
 *       must require authentication. The catch-all rule covers both, but the
 *       test makes it explicit so a future per-prefix carve-out cannot bypass.</li>
 *   <li>The Atom binding (XML response) does not relax security relative to
 *       the Browser binding (JSON response).</li>
 *   <li>Per-object ACL violations from the underlying service throw
 *       {@code SecurityException}, which the controller wraps as
 *       {@code ResponseStatusException(FORBIDDEN, ...)} — so the CMIS client
 *       sees 403 (not a leaked 500 or 401).</li>
 * </ol>
 *
 * <p>Production drift on the {@code /api/cmis/**} matcher is guarded
 * separately by {@code SecurityConfigProtocolSecurityTest} (the inverse claim:
 * CMIS paths must NOT be permitAll).
 */
@WebMvcTest(controllers = CmisAtomPubController.class)
@ContextConfiguration(classes = {
    CmisAtomPubController.class,
    RestExceptionHandler.class,
    CmisAtomPubControllerSecurityTest.TestSecurityConfig.class
})
class CmisAtomPubControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CmisBrowserService browserService;

    @MockBean
    private CmisMutationService mutationService;

    @MockBean
    private CmisContentVersioningService contentVersioningService;

    @MockBean
    private CmisAtomPubSerializer serializer;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    // mirrors prod SecurityConfig — CMIS paths fall under
                    // /api/** which requires authentication.
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});
            return http.build();
        }
    }

    @Test
    @DisplayName("unauthenticated GET /api/cmis/atom (service doc) returns 401")
    void unauthenticatedServiceDocReturns401() throws Exception {
        mockMvc.perform(get("/api/cmis/atom").accept("application/atomsvc+xml"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /api/v1/cmis/atom (service doc, /v1 prefix) returns 401")
    void unauthenticatedServiceDocV1ReturnsAlso401() throws Exception {
        // Both prefixes must be gated. A regression carving out either one
        // would bypass auth and is caught here.
        mockMvc.perform(get("/api/v1/cmis/atom").accept("application/atomsvc+xml"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /api/cmis/atom/object (Atom-XML object) returns 401")
    void unauthenticatedGetObjectReturns401() throws Exception {
        mockMvc.perform(get("/api/cmis/atom/object").param("objectId", "abc"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /api/cmis/atom/document returns 401")
    void unauthenticatedCreateDocumentReturns401() throws Exception {
        mockMvc.perform(post("/api/cmis/atom/document")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /api/cmis/atom/object returns 401")
    void unauthenticatedDeleteObjectReturns401() throws Exception {
        mockMvc.perform(delete("/api/cmis/atom/object").param("objectId", "abc"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated GET /api/cmis/atom returns the Atom service document")
    void authenticatedServiceDocReturnsOk() throws Exception {
        when(browserService.getRepositoryInfo()).thenReturn(new CmisModels.RepositoryInfo(
            "athena", "Athena", "Athena", "Athena ECM", "1.0",
            "1.1", "root-id", java.util.List.of()));
        when(serializer.serializeServiceDocument(any(), anyString())).thenReturn("<service/>");

        mockMvc.perform(get("/api/cmis/atom").accept("application/atomsvc+xml"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated GET /api/cmis/atom/object — service SecurityException maps to 403, NOT 401")
    void perObjectAclViolationMapsToForbidden() throws Exception {
        // The CMIS client-visible behavior is: per-object ACL denial → 403.
        // If the controller's catch(SecurityException) block ever stopped
        // mapping to FORBIDDEN, the user would see 500 (or 401 from a
        // misconfigured filter chain), confusing CMIS clients.
        when(browserService.getObject(eq("denied"), any()))
            .thenThrow(new SecurityException("CMIS ACL denied: read on object denied"));

        mockMvc.perform(get("/api/cmis/atom/object").param("objectId", "denied"))
            .andExpect(status().isForbidden());
    }
}
