package com.ecm.core.controller;

import com.ecm.core.cmis.CmisAclService;
import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisChangeLogService;
import com.ecm.core.cmis.CmisContentVersioningService;
import com.ecm.core.cmis.CmisModels;
import com.ecm.core.cmis.CmisMutationService;
import com.ecm.core.cmis.CmisQueryService;
import com.ecm.core.cmis.CmisRelationshipService;
import com.ecm.core.cmis.CmisRenditionService;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security test for {@link CmisBrowserController}.
 *
 * Companion to {@link CmisAtomPubControllerSecurityTest}. The Browser binding
 * (JSON responses) shares the same JWT-bearer auth gate as the Atom binding —
 * both fall under {@code /api/**} → {@code authenticated()} in production
 * {@code SecurityConfig}.
 *
 * <p>Browser-binding-specific concerns documented here:
 *
 * <ol>
 *   <li>Both URL prefixes ({@code /api/cmis/browser} and
 *       {@code /api/v1/cmis/browser}) require authentication.</li>
 *   <li>The unified GET selector entrypoint ({@code ?cmisselector=...}) and
 *       the unified POST mutation entrypoint ({@code ?cmisaction=...})
 *       are equally gated — one shared auth check, not selector/action-
 *       specific.</li>
 *   <li>The dedicated {@code POST /acl} sub-route is also gated.</li>
 *   <li>{@code SecurityException} from any service layer is mapped to 403 by
 *       the controller's try/catch — surfacing per-object ACL violations as
 *       the CMIS-correct status (not a leaked 500).</li>
 * </ol>
 */
@WebMvcTest(controllers = CmisBrowserController.class)
@ContextConfiguration(classes = {
    CmisBrowserController.class,
    RestExceptionHandler.class,
    CmisBrowserControllerSecurityTest.TestSecurityConfig.class
})
class CmisBrowserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CmisAclService cmisAclService;

    @MockBean
    private CmisBrowserService cmisBrowserService;

    @MockBean
    private CmisChangeLogService cmisChangeLogService;

    @MockBean
    private CmisQueryService cmisQueryService;

    @MockBean
    private CmisMutationService cmisMutationService;

    @MockBean
    private CmisContentVersioningService cmisContentVersioningService;

    @MockBean
    private CmisRelationshipService cmisRelationshipService;

    @MockBean
    private CmisRenditionService cmisRenditionService;

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
    @DisplayName("unauthenticated GET /api/cmis/browser returns 401")
    void unauthenticatedGetReturns401() throws Exception {
        mockMvc.perform(get("/api/cmis/browser").param("cmisselector", "repositoryInfo"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /api/v1/cmis/browser (/v1 prefix) returns 401")
    void unauthenticatedGetV1Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/cmis/browser").param("cmisselector", "repositoryInfo"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /api/cmis/browser (mutation entrypoint) returns 401")
    void unauthenticatedPostReturns401() throws Exception {
        mockMvc.perform(post("/api/cmis/browser")
                .param("cmisaction", "createDocument")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /api/cmis/browser/acl returns 401")
    void unauthenticatedAclReturns401() throws Exception {
        mockMvc.perform(post("/api/cmis/browser/acl")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /api/cmis/browser/relationships returns 401")
    void unauthenticatedRelationshipsReturns401() throws Exception {
        mockMvc.perform(post("/api/cmis/browser/relationships")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated GET /api/cmis/browser?cmisselector=repositoryInfo returns 200 (JSON)")
    void authenticatedRepositoryInfoReturnsJson() throws Exception {
        when(cmisBrowserService.getRepositoryInfo()).thenReturn(new CmisModels.RepositoryInfo(
            "athena", "Athena", "Athena", "Athena ECM", "1.0",
            "1.1", "root-id", List.of()));

        mockMvc.perform(get("/api/cmis/browser").param("cmisselector", "repositoryInfo"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated POST /acl with service SecurityException maps to 403, NOT 401")
    void aclSecurityExceptionMapsToForbidden() throws Exception {
        // The controller's try/catch in applyAcl() converts SecurityException → 403.
        // If a future refactor removed that catch, the user would see 500 instead.
        when(cmisAclService.applyAcl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new SecurityException("CMIS ACL denied: changePermissions on object xyz"));

        mockMvc.perform(post("/api/cmis/browser/acl")
                .contentType("application/json")
                .content("{\"objectId\":\"xyz\",\"addAces\":[],\"removeAces\":[]}"))
            .andExpect(status().isForbidden());
    }
}
