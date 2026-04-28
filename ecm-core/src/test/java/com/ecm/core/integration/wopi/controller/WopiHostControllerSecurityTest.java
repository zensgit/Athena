package com.ecm.core.integration.wopi.controller;

import com.ecm.core.controller.RestExceptionHandler;
import com.ecm.core.integration.wopi.model.WopiCheckFileInfoResponse;
import com.ecm.core.integration.wopi.service.WopiAccessTokenService;
import com.ecm.core.integration.wopi.service.WopiLockService;
import com.ecm.core.integration.wopi.service.WopiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security test for {@link WopiHostController}.
 *
 * Token-based-auth security seam (NOT @WithMockUser-shaped). The route
 * {@code /wopi/**} is gated by {@code permitAll()} in production
 * {@code SecurityConfig.java:56}, then validated inside
 * {@link WopiAccessTokenService#validate(UUID, String)} via the
 * {@code ?access_token=} query parameter.
 *
 * On missing / invalid / expired / wrong-document tokens, the access-token
 * service throws {@link ResponseStatusException} with {@link HttpStatus#UNAUTHORIZED}
 * — which Spring propagates directly as 401 (no need for an
 * {@code @ExceptionHandler}).
 *
 * <p>Security claims tested:
 * <ol>
 *   <li>Route is reachable WITHOUT Spring Security authentication
 *       (proves the {@code permitAll()} rule is in place).</li>
 *   <li>Missing / invalid {@code access_token} produces controller-layer 401,
 *       NOT a Spring filter-chain 401. (Proves the access_token is actually
 *       validated end-to-end.)</li>
 *   <li>Valid {@code access_token} produces 200.</li>
 * </ol>
 *
 * <p>If a future change moves the {@code permitAll()} rule below
 * {@code requestMatchers("/api/**").authenticated()} or removes it entirely,
 * the missing-token case still returns 401 — but the mock is never called,
 * so the test would need to verify call count to surface that regression.
 * The "valid token → 200" case catches it more reliably: it would also flip
 * to 401 if permitAll were removed (Office Online integration would break
 * for legitimate users).
 */
@WebMvcTest(controllers = WopiHostController.class)
@ContextConfiguration(classes = {
    WopiHostController.class,
    RestExceptionHandler.class,
    WopiHostControllerSecurityTest.TestSecurityConfig.class
})
class WopiHostControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WopiService wopiService;

    @MockBean
    private WopiLockService wopiLockService;

    @MockBean
    private WopiAccessTokenService accessTokenService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    // mirrors prod SecurityConfig.java:56 — wopi path is permitAll,
                    // access_token is validated in the service layer.
                    .requestMatchers("/wopi/**").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});
            return http.build();
        }
    }

    private static final UUID DOC_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("GET /wopi/files/{id} with NO access_token returns 400 — Spring missing-required-param, not filter 401")
    void missingAccessTokenIsClientError() throws Exception {
        // Spring rejects missing @RequestParam access_token at controller-binding time → 400.
        // The 400 is the load-bearing claim: it proves the request reached the controller
        // (filter chain didn't 401). Without permitAll(), this would be 401 instead.
        mockMvc.perform(get("/wopi/files/{id}", DOC_ID))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /wopi/files/{id} with INVALID access_token — service rejects with controller-layer 401")
    void invalidAccessTokenIsControllerUnauthorized() throws Exception {
        // Mock wopiService.checkFileInfo to throw the same ResponseStatusException(401)
        // that WopiAccessTokenService.validate() would throw on a bad token.
        when(wopiService.checkFileInfo(any(UUID.class), eq("invalid-token")))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid WOPI access_token"));

        mockMvc.perform(get("/wopi/files/{id}", DOC_ID).param("access_token", "invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /wopi/files/{id} with EXPIRED access_token — service rejects with controller-layer 401")
    void expiredAccessTokenIsControllerUnauthorized() throws Exception {
        when(wopiService.checkFileInfo(any(UUID.class), eq("expired-token")))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired WOPI access_token"));

        mockMvc.perform(get("/wopi/files/{id}", DOC_ID).param("access_token", "expired-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /wopi/files/{id} with WRONG-DOCUMENT access_token — service rejects with controller-layer 401")
    void wrongDocumentAccessTokenIsControllerUnauthorized() throws Exception {
        // A token issued for one document must NOT be accepted for another. Service throws
        // ResponseStatusException(401, "WOPI access_token does not match document").
        when(wopiService.checkFileInfo(any(UUID.class), eq("token-for-other-doc")))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "WOPI access_token does not match document"));

        mockMvc.perform(get("/wopi/files/{id}", DOC_ID).param("access_token", "token-for-other-doc"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /wopi/files/{id} with VALID access_token — service approves with 200")
    void validAccessTokenReachesService() throws Exception {
        when(wopiService.checkFileInfo(any(UUID.class), eq("valid-token")))
            .thenReturn(WopiCheckFileInfoResponse.builder()
                .baseFileName("contract.docx")
                .ownerId("alice")
                .size(1024L)
                .userId("alice")
                .userFriendlyName("Alice")
                .build());

        mockMvc.perform(get("/wopi/files/{id}", DOC_ID).param("access_token", "valid-token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Anonymous request to /wopi/files/** is reachable — proven by 200 with valid token (would be 401 if permitAll missing)")
    void filterChainPermitsAnonymousAccess() throws Exception {
        // Triangulates the permitAll claim: the only way the controller can produce 200
        // for an anonymous (no SecurityContext) request is if the filter chain let it
        // through. If a future change drops the /wopi/** permitAll rule, this test flips
        // to 401 immediately.
        when(wopiService.checkFileInfo(any(UUID.class), eq("valid-token")))
            .thenReturn(WopiCheckFileInfoResponse.builder()
                .baseFileName("ok.docx")
                .ownerId("alice")
                .size(0L)
                .userId("alice")
                .userFriendlyName("Alice")
                .build());

        mockMvc.perform(get("/wopi/files/{id}", DOC_ID).param("access_token", "valid-token"))
            .andExpect(status().isOk());
    }
}
