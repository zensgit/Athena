package com.ecm.core.controller;

import com.ecm.core.dto.StorageCapacityStatusDto;
import com.ecm.core.service.StorageCapacityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorageAdminControllerTest {

    @Mock
    private StorageCapacityService storageCapacityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StorageAdminController(storageCapacityService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /admin/storage/capacity returns filesystem capacity status")
    void getStorageCapacityReturnsStatus() throws Exception {
        when(storageCapacityService.getStatus()).thenReturn(new StorageCapacityStatusDto(
            "filesystem",
            "WARN",
            1000L,
            150L,
            850L,
            85.0d,
            80,
            95,
            104857600L,
            "/var/ecm/content",
            null
        ));

        mockMvc.perform(get("/api/v1/admin/storage/capacity"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.backendType").value("filesystem"))
            .andExpect(jsonPath("$.status").value("WARN"))
            .andExpect(jsonPath("$.totalBytes").value(1000))
            .andExpect(jsonPath("$.usableBytes").value(150))
            .andExpect(jsonPath("$.usedBytes").value(850))
            .andExpect(jsonPath("$.usedPercent").value(85.0d))
            .andExpect(jsonPath("$.warnPercent").value(80))
            .andExpect(jsonPath("$.criticalPercent").value(95))
            .andExpect(jsonPath("$.blockedMinFreeBytes").value(104857600))
            .andExpect(jsonPath("$.rootPath").value("/var/ecm/content"))
            .andExpect(jsonPath("$.error").doesNotExist());
    }
}
