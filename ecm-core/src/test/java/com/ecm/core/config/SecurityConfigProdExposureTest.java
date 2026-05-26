package com.ecm.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0a-2 drift guard for production exposure controls: health-only actuator, no anonymous
 * Swagger/OpenAPI, and explicit CORS origins. Uses prod-like properties without a full app boot.
 */
@WebMvcTest(controllers = SecurityConfigProdExposureTest.ProdExposureProbeController.class)
@ContextConfiguration(classes = {
    SecurityConfig.class,
    SecurityConfigProdExposureTest.ProdExposureProbeController.class
})
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/.well-known/jwks.json",
    "ecm.security.exposure.actuator-permit-all=false",
    "ecm.security.exposure.swagger-permit-all=false",
    "ecm.security.cors.allowed-origins=https://athena.example.com,https://admin.example.com"
})
class SecurityConfigProdExposureTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("prod-like config permits anonymous health only")
    void prodExposurePermitsAnonymousHealthOnly() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("prod-like config requires admin for non-health actuator endpoints")
    @WithMockUser(roles = "USER")
    void prodExposureRejectsNonAdminActuatorAccess() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("prod-like config permits admin for non-health actuator endpoints")
    @WithMockUser(roles = "ADMIN")
    void prodExposurePermitsAdminActuatorAccess() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("prod-like config does not expose Swagger or OpenAPI anonymously")
    void prodExposureProtectsSwaggerAndOpenApi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("prod-like config keeps ordinary API routes authenticated")
    void prodExposureKeepsOrdinaryApiProtected() throws Exception {
        mockMvc.perform(get("/api/v1/protected/probe"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("prod-like config only allows configured CORS origins")
    void prodExposureRestrictsCorsOrigins() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.ORIGIN, "https://athena.example.com"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://athena.example.com"));

        mockMvc.perform(get("/actuator/health").header(HttpHeaders.ORIGIN, "https://evil.example.com"))
            .andExpect(status().isForbidden())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, nullValue()));
    }

    @RestController
    static class ProdExposureProbeController {
        @GetMapping("/actuator/health")
        ResponseEntity<Void> health() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/actuator/metrics")
        ResponseEntity<Void> metrics() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/swagger-ui/index.html")
        ResponseEntity<Void> swagger() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/v3/api-docs")
        ResponseEntity<Void> openApi() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/v1/protected/probe")
        ResponseEntity<Void> protectedApi() {
            return ResponseEntity.ok().build();
        }
    }
}
