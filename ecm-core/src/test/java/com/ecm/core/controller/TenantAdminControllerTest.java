package com.ecm.core.controller;

import com.ecm.core.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TenantAdminControllerTest {

    @Mock private TenantService tenantService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TenantAdminController controller = new TenantAdminController(tenantService);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /admin/tenants lists tenants")
    void listTenantsReturnsRegistry() throws Exception {
        when(tenantService.listTenants()).thenReturn(List.of(dto("default", "Default Tenant", true, true)));

        mockMvc.perform(get("/api/v1/admin/tenants"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tenantDomain").value("default"))
            .andExpect(jsonPath("$[0].systemDefault").value(true));
    }

    @Test
    @DisplayName("GET /admin/tenants/current returns current tenant")
    void getCurrentTenantReturnsResolvedTenant() throws Exception {
        when(tenantService.getCurrentTenant()).thenReturn(dto("default", "Default Tenant", true, true));

        mockMvc.perform(get("/api/v1/admin/tenants/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantDomain").value("default"));
    }

    @Test
    @DisplayName("POST /admin/tenants creates tenant")
    void createTenantReturnsCreated() throws Exception {
        when(tenantService.createTenant(any())).thenReturn(dto("acme", "Acme Corp", true, false));

        mockMvc.perform(post("/api/v1/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantDomain": "acme",
                      "tenantName": "Acme Corp",
                      "enabled": true,
                      "quotaBytes": 1024
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tenantDomain").value("acme"));
    }

    @Test
    @DisplayName("PUT /admin/tenants/{domain} updates tenant")
    void updateTenantReturnsUpdatedTenant() throws Exception {
        when(tenantService.updateTenant(eq("acme"), any())).thenReturn(dto("acme", "Acme Updated", true, false));

        mockMvc.perform(put("/api/v1/admin/tenants/{tenantDomain}", "acme")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantDomain": "acme",
                      "tenantName": "Acme Updated",
                      "enabled": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantName").value("Acme Updated"));
    }

    @Test
    @DisplayName("DELETE /admin/tenants/{domain} returns no content")
    void deleteTenantReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/tenants/{tenantDomain}", "acme"))
            .andExpect(status().isNoContent());
    }

    private TenantService.TenantDto dto(String domain, String name, boolean enabled, boolean systemDefault) {
        return new TenantService.TenantDto(
            UUID.randomUUID(),
            domain,
            name,
            enabled,
            null,
            1024L,
            systemDefault,
            LocalDateTime.of(2026, 4, 6, 10, 0),
            LocalDateTime.of(2026, 4, 6, 11, 0)
        );
    }
}
