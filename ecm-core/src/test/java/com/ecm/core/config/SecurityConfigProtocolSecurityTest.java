package com.ecm.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drift guard for protocol route matcher semantics in production SecurityConfig.
 */
@WebMvcTest(controllers = SecurityConfigProtocolSecurityTest.ProtocolProbeController.class)
@ContextConfiguration(classes = {
    SecurityConfig.class,
    SecurityConfigProtocolSecurityTest.ProtocolProbeController.class
})
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/.well-known/jwks.json"
})
class SecurityConfigProtocolSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("production SecurityConfig permits anonymous Transfer receiver protocol path")
    void productionSecurityConfigPermitsAnonymousTransferReceiverPath() throws Exception {
        mockMvc.perform(get("/api/v1/transfer/receiver/verify")
                .param("folderId", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("production SecurityConfig ignores opaque Bearer headers on Transfer receiver path")
    void productionSecurityConfigIgnoresOpaqueBearerHeadersOnTransferReceiverPath() throws Exception {
        mockMvc.perform(get("/api/v1/transfer/receiver/verify")
                .param("folderId", UUID.randomUUID().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-transfer-secret"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("production SecurityConfig permits anonymous WOPI host protocol path")
    void productionSecurityConfigPermitsAnonymousWopiPath() throws Exception {
        mockMvc.perform(get("/wopi/files/{id}", UUID.randomUUID())
                .param("access_token", "opaque-wopi-token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("production SecurityConfig ignores opaque Bearer headers on WOPI host path")
    void productionSecurityConfigIgnoresOpaqueBearerHeadersOnWopiPath() throws Exception {
        mockMvc.perform(get("/wopi/files/{id}", UUID.randomUUID())
                .param("access_token", "opaque-wopi-token")
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-wopi-token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("production SecurityConfig still protects ordinary API routes")
    void productionSecurityConfigStillProtectsOrdinaryApiRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/protected/probe"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("production SecurityConfig protects /api/v1/transfer/targets — sender-side transfer is NOT permitAll")
    void productionSecurityConfigProtectsTransferReplicationPath() throws Exception {
        // Receiver path is intentionally permitAll, but sender/admin transfer
        // routes must stay behind ordinary /api/** authentication.
        mockMvc.perform(get("/api/v1/transfer/targets"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("production SecurityConfig protects /api/v1/replication/jobs — replication ops are NOT permitAll")
    void productionSecurityConfigProtectsReplicationJobsPath() throws Exception {
        mockMvc.perform(get("/api/v1/replication/jobs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("production SecurityConfig protects /api/v1/integration/wopi/health — app WOPI companion is NOT permitAll")
    void productionSecurityConfigProtectsWopiIntegrationPath() throws Exception {
        // /wopi/** host protocol is permitAll, but application-facing WOPI
        // integration endpoints must remain authenticated.
        mockMvc.perform(get("/api/v1/integration/wopi/health"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("production SecurityConfig protects /api/v1/integration/wopi/url/{id} — app WOPI URL is NOT permitAll")
    void productionSecurityConfigProtectsWopiIntegrationUrlPath() throws Exception {
        mockMvc.perform(get("/api/v1/integration/wopi/url/{documentId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("production SecurityConfig protects /api/cmis/atom — CMIS is NOT permitAll (inverse drift guard)")
    void productionSecurityConfigProtectsCmisAtomPath() throws Exception {
        // Inverse claim: CMIS routes must remain authenticated. If a future
        // change accidentally adds /api/cmis/** or /api/v1/cmis/** to the
        // permitAll list (e.g. while wiring a public CMIS landing page), this
        // test flips from 401 to 200 and fails immediately.
        mockMvc.perform(get("/api/cmis/atom"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("production SecurityConfig protects /api/v1/cmis/atom — v1 CMIS is NOT permitAll")
    void productionSecurityConfigProtectsCmisAtomV1Path() throws Exception {
        mockMvc.perform(get("/api/v1/cmis/atom"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("production SecurityConfig protects /api/cmis/browser — CMIS is NOT permitAll (inverse drift guard)")
    void productionSecurityConfigProtectsCmisBrowserPath() throws Exception {
        mockMvc.perform(get("/api/cmis/browser").param("cmisselector", "repositoryInfo"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("production SecurityConfig protects /api/v1/cmis/browser — v1 CMIS is NOT permitAll")
    void productionSecurityConfigProtectsCmisBrowserV1Path() throws Exception {
        mockMvc.perform(get("/api/v1/cmis/browser").param("cmisselector", "repositoryInfo"))
            .andExpect(status().isUnauthorized());
    }

    @RestController
    static class ProtocolProbeController {
        @GetMapping("/api/v1/transfer/receiver/verify")
        ResponseEntity<Void> transferReceiverProbe(@RequestParam UUID folderId) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/wopi/files/{id}")
        ResponseEntity<Void> wopiProbe(@PathVariable UUID id, @RequestParam("access_token") String accessToken) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/v1/protected/probe")
        ResponseEntity<Void> protectedApiProbe() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/v1/transfer/targets")
        ResponseEntity<Void> transferReplicationProbe() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/v1/replication/jobs")
        ResponseEntity<Void> replicationJobsProbe() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/v1/integration/wopi/health")
        ResponseEntity<Void> wopiIntegrationProbe() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/v1/integration/wopi/url/{documentId}")
        ResponseEntity<Void> wopiIntegrationUrlProbe(@PathVariable UUID documentId) {
            return ResponseEntity.ok().build();
        }

        // CMIS probe endpoints — used only by the "must remain authenticated"
        // assertions. They must never return 200 in this test (filter chain
        // should reject before they run).
        @GetMapping("/api/cmis/atom")
        ResponseEntity<Void> cmisAtomProbe() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/v1/cmis/atom")
        ResponseEntity<Void> cmisAtomV1Probe() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/cmis/browser")
        ResponseEntity<Void> cmisBrowserProbe(@RequestParam(name = "cmisselector", required = false) String selector) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/v1/cmis/browser")
        ResponseEntity<Void> cmisBrowserV1Probe(@RequestParam(name = "cmisselector", required = false) String selector) {
            return ResponseEntity.ok().build();
        }
    }
}
