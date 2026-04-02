package com.ecm.core.controller;

import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.ModelStatus;
import com.ecm.core.repository.ContentModelDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DiscoveryControllerTest2 {

    private MockMvc mockMvc;
    @Mock private ContentModelDefinitionRepository contentModelRepo;

    @BeforeEach
    void setUp() {
        DiscoveryController controller = new DiscoveryController(contentModelRepo);
        ReflectionTestUtils.setField(controller, "version", "2.5.0");
        ReflectionTestUtils.setField(controller, "buildNumber", "1234");
        ReflectionTestUtils.setField(controller, "buildDate", "2026-03-30");
        ReflectionTestUtils.setField(controller, "edition", "Community");
        ReflectionTestUtils.setField(controller, "applicationName", "ecm-core");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("returns repository info with version and edition")
    void returnsRepositoryInfo() throws Exception {
        when(contentModelRepo.findByStatus(ModelStatus.ACTIVE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/discovery"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repository.id").value("ecm-core"))
            .andExpect(jsonPath("$.repository.edition").value("Community"))
            .andExpect(jsonPath("$.repository.version.display").value("2.5.0"))
            .andExpect(jsonPath("$.repository.version.buildNumber").value("1234"))
            .andExpect(jsonPath("$.repository.version.buildDate").value("2026-03-30"));
    }

    @Test
    @DisplayName("returns modules list with at least ecm-core")
    void returnsModules() throws Exception {
        when(contentModelRepo.findByStatus(ModelStatus.ACTIVE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/discovery"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repository.modules").isArray())
            .andExpect(jsonPath("$.repository.modules.length()").value(12))
            .andExpect(jsonPath("$.repository.modules[0].id").value("ecm-core"))
            .andExpect(jsonPath("$.repository.modules[0].version").value("2.5.0"));
    }

    @Test
    @DisplayName("returns capabilities list including core ECM features")
    void returnsCapabilities() throws Exception {
        when(contentModelRepo.findByStatus(ModelStatus.ACTIVE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/discovery"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repository.capabilities").isArray())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='versioning')]").exists())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='checkout')]").exists())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='working-copy')]").exists())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='locking')]").exists())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='content-models')]").exists())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='aspects')]").exists())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='associations')]").exists())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='share-links')]").exists())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='workflow')]").exists())
            .andExpect(jsonPath("$.repository.capabilities[?(@=='pdf-annotations')]").exists());
    }

    @Test
    @DisplayName("returns running status with timestamp")
    void returnsStatus() throws Exception {
        when(contentModelRepo.findByStatus(ModelStatus.ACTIVE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/discovery"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repository.status.state").value("RUNNING"))
            .andExpect(jsonPath("$.repository.status.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("metrics includes active content model count")
    void includesContentModelMetrics() throws Exception {
        ContentModelDefinition model = new ContentModelDefinition();
        model.setStatus(ModelStatus.ACTIVE);
        when(contentModelRepo.findByStatus(ModelStatus.ACTIVE)).thenReturn(List.of(model, model));

        mockMvc.perform(get("/api/v1/discovery"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repository.metrics.activeContentModels").value(2));
    }

    @Test
    @DisplayName("also accessible at /api/discovery (no v1 prefix)")
    void accessibleWithoutV1() throws Exception {
        when(contentModelRepo.findByStatus(ModelStatus.ACTIVE)).thenReturn(List.of());

        mockMvc.perform(get("/api/discovery"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repository").exists());
    }
}
