package com.ecm.core.controller;

import com.ecm.core.service.RecordsManagementService;
import com.ecm.core.service.RmReportPresetService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecordsManagementController.class)
@ContextConfiguration(classes = {
    RecordsManagementController.class,
    RestExceptionHandler.class,
    RecordsManagementControllerSecurityTest.TestSecurityConfig.class
})
class RecordsManagementControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecordsManagementService recordsManagementService;

    @MockBean
    private RmReportPresetService rmReportPresetService;

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
    @DisplayName("records management endpoints require authentication")
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/records"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("non-admin users cannot access records management endpoints")
    void nonAdminUsersCannotAccessEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/records"))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/records/file-plans/{folderId}", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "description": "Updated description"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/records/file-plans/{folderId}/rename", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "name": "HR File Plan"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/records/file-plans/{folderId}/move", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "targetParentId": "%s"
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/records/categories/{categoryId}/rename", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "name": "Agreements"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/records/categories/{categoryId}/move", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "targetParentId": "%s"
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/records/categories/{categoryId}", UUID.randomUUID()))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/record/undeclare", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "reason": "Administrative correction"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admins can access records management endpoints")
    void adminsCanAccessEndpoints() throws Exception {
        when(recordsManagementService.listRecords()).thenReturn(List.of());
        when(recordsManagementService.updateFilePlan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new RecordsManagementService.FilePlanDto(
                UUID.randomUUID(),
                "Corporate File Plan",
                "Updated description",
                "/Corporate File Plan",
                null
            ));
        when(recordsManagementService.renameFilePlan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new RecordsManagementService.FilePlanDto(
                UUID.randomUUID(),
                "HR File Plan",
                "Updated description",
                "/HR File Plan",
                null
            ));
        when(recordsManagementService.moveFilePlan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new RecordsManagementService.FilePlanDto(
                UUID.randomUUID(),
                "HR File Plan",
                "Updated description",
                "/Company Home/HR File Plan",
                UUID.randomUUID()
            ));
        when(recordsManagementService.renameRecordCategory(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new RecordsManagementService.RecordCategoryDto(
                UUID.randomUUID(),
                "Agreements",
                "Updated description",
                "/Records Management/Agreements",
                1,
                UUID.randomUUID()
            ));
        when(recordsManagementService.moveRecordCategory(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new RecordsManagementService.RecordCategoryDto(
                UUID.randomUUID(),
                "Contracts",
                "Updated description",
                "/Records Management/Finance/Contracts",
                2,
                UUID.randomUUID()
            ));

        mockMvc.perform(get("/api/v1/records"))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/records/file-plans/{folderId}", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "description": "Updated description"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/records/file-plans/{folderId}/rename", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "name": "HR File Plan"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/records/file-plans/{folderId}/move", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "targetParentId": "%s"
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/records/categories/{categoryId}/rename", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "name": "Agreements"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/records/categories/{categoryId}/move", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                    {
                      "targetParentId": "%s"
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/records/categories/{categoryId}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
