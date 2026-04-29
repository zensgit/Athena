package com.ecm.core.controller;

import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.service.PropertyEncryptionOperationsService;
import com.ecm.core.service.PropertyEncryptionOperationsService.EncryptedPropertyDefinitionSummary;
import com.ecm.core.service.PropertyEncryptionOperationsService.KeyVersionValueCount;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyBackfillCount;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunRequest;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunResult;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionRewrapDryRunRequest;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionRewrapDryRunResult;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionStatus;
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
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PropertyEncryptionOperationsController.class)
@ContextConfiguration(classes = {
    PropertyEncryptionOperationsController.class,
    PropertyEncryptionOperationsControllerSecurityTest.TestSecurityConfig.class
})
class PropertyEncryptionOperationsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PropertyEncryptionOperationsService propertyEncryptionOperationsService;

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
    @DisplayName("anonymous property encryption operations request returns 401")
    void anonymousStatusReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/property-encryption/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("property encryption operations require admin role")
    void operationsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/property-encryption/status"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/property-encryption/definitions"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/property-encryption/rewrap-jobs/dry-run")
                .contentType("application/json")
                .content("{\"targetKeyVersion\":\"v2\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/property-encryption/backfill-jobs/dry-run")
                .contentType("application/json")
                .content("{\"targetKeyVersion\":\"v2\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can load property encryption status")
    void adminCanLoadStatus() throws Exception {
        when(propertyEncryptionOperationsService.getStatus()).thenReturn(new PropertyEncryptionStatus(
            true,
            "v2",
            true,
            List.of("v1", "v2"),
            2,
            1,
            1,
            3,
            5,
            List.of()
        ));

        mockMvc.perform(get("/api/v1/admin/property-encryption/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.secretCryptoEnabled", is(true)))
            .andExpect(jsonPath("$.activeKeyVersion", is("v2")))
            .andExpect(jsonPath("$.activeKeyConfigured", is(true)))
            .andExpect(jsonPath("$.configuredKeyVersions", hasSize(2)))
            .andExpect(jsonPath("$.encryptedPropertyDefinitionCount", is(2)))
            .andExpect(jsonPath("$.nodesWithEncryptedPropertiesCount", is(3)))
            .andExpect(jsonPath("$.encryptedPropertyValueCount", is(5)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can list encrypted property definitions")
    void adminCanListDefinitions() throws Exception {
        when(propertyEncryptionOperationsService.listEncryptedDefinitions()).thenReturn(List.of(
            new EncryptedPropertyDefinitionSummary(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "acme:secretCode",
                "secretCode",
                "Secret code",
                "TYPE",
                "acme:contract",
                PropertyDataType.TEXT,
                false,
                false,
                false
            )
        ));

        mockMvc.perform(get("/api/v1/admin/property-encryption/definitions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].qualifiedName", is("acme:secretCode")))
            .andExpect(jsonPath("$[0].ownerKind", is("TYPE")))
            .andExpect(jsonPath("$[0].ownerQName", is("acme:contract")))
            .andExpect(jsonPath("$[0].dataType", is("TEXT")))
            .andExpect(jsonPath("$[0].indexed", is(false)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can dry-run property encryption rewrap without creating a job")
    void adminCanDryRunRewrap() throws Exception {
        when(propertyEncryptionOperationsService.dryRunRewrap(new PropertyEncryptionRewrapDryRunRequest("v2")))
            .thenReturn(new PropertyEncryptionRewrapDryRunResult(
                "v2",
                true,
                true,
                4,
                7,
                2,
                5,
                0,
                List.of(
                    new KeyVersionValueCount("v1", 5),
                    new KeyVersionValueCount("v2", 2)
                ),
                List.of(),
                List.of(),
                true
            ));

        mockMvc.perform(post("/api/v1/admin/property-encryption/rewrap-jobs/dry-run")
                .contentType("application/json")
                .content("{\"targetKeyVersion\":\"v2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetKeyVersion", is("v2")))
            .andExpect(jsonPath("$.targetKeyConfigured", is(true)))
            .andExpect(jsonPath("$.secretCryptoEnabled", is(true)))
            .andExpect(jsonPath("$.candidateNodeCount", is(4)))
            .andExpect(jsonPath("$.encryptedPropertyValueCount", is(7)))
            .andExpect(jsonPath("$.valuesAlreadyOnTargetKeyCount", is(2)))
            .andExpect(jsonPath("$.valuesRequiringRewrapCount", is(5)))
            .andExpect(jsonPath("$.unversionedOrMalformedValueCount", is(0)))
            .andExpect(jsonPath("$.keyVersionCounts", hasSize(2)))
            .andExpect(jsonPath("$.keyVersionCounts[0].keyVersion", is("v1")))
            .andExpect(jsonPath("$.keyVersionCounts[0].encryptedPropertyValueCount", is(5)))
            .andExpect(jsonPath("$.missingSourceKeyVersions", hasSize(0)))
            .andExpect(jsonPath("$.executable", is(true)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can dry-run property encryption backfill without creating a job")
    void adminCanDryRunBackfill() throws Exception {
        when(propertyEncryptionOperationsService.dryRunBackfill(new PropertyEncryptionBackfillDryRunRequest("v2")))
            .thenReturn(new PropertyEncryptionBackfillDryRunResult(
                "v2",
                true,
                true,
                1,
                4,
                2,
                0,
                4,
                0,
                List.of(new PropertyBackfillCount(
                    "acme:secretCode",
                    "TYPE",
                    "acme:contract",
                    4,
                    2,
                    0,
                    4
                )),
                List.of(),
                true
            ));

        mockMvc.perform(post("/api/v1/admin/property-encryption/backfill-jobs/dry-run")
                .contentType("application/json")
                .content("{\"targetKeyVersion\":\"v2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetKeyVersion", is("v2")))
            .andExpect(jsonPath("$.targetKeyConfigured", is(true)))
            .andExpect(jsonPath("$.secretCryptoEnabled", is(true)))
            .andExpect(jsonPath("$.encryptedPropertyDefinitionCount", is(1)))
            .andExpect(jsonPath("$.plaintextValueCount", is(4)))
            .andExpect(jsonPath("$.alreadyEncryptedValueCount", is(2)))
            .andExpect(jsonPath("$.dualStorageConflictValueCount", is(0)))
            .andExpect(jsonPath("$.readyValueCount", is(4)))
            .andExpect(jsonPath("$.orphanEncryptedValueCount", is(0)))
            .andExpect(jsonPath("$.definitionCounts", hasSize(1)))
            .andExpect(jsonPath("$.definitionCounts[0].qualifiedName", is("acme:secretCode")))
            .andExpect(jsonPath("$.definitionCounts[0].readyValueCount", is(4)))
            .andExpect(jsonPath("$.executable", is(true)));
    }
}
