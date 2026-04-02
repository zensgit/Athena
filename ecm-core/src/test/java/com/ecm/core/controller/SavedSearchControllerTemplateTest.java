package com.ecm.core.controller;

import com.ecm.core.service.SavedSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SavedSearchControllerTemplateTest {

    private MockMvc mockMvc;

    @Mock
    private SavedSearchService savedSearchService;

    @BeforeEach
    void setUp() {
        SavedSearchController controller = new SavedSearchController(savedSearchService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Templates endpoint returns built-in templates")
    void getSavedSearchTemplatesShouldReturnTemplates() throws Exception {
        when(savedSearchService.listBuiltInTemplates(null)).thenReturn(List.of(
            new SavedSearchService.SavedSearchTemplate(
                "failed-preview-last7d",
                "Failed Preview (Last 7 Days)",
                "Find recently failed preview documents.",
                Map.of("previewStatus", List.of("FAILED"), "dateRange", "week"),
                List.of("governance", "preview")
            )
        ));

        mockMvc.perform(get("/api/v1/search/saved/templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("failed-preview-last7d"))
            .andExpect(jsonPath("$[0].tags[0]").value("governance"));

        verify(savedSearchService).listBuiltInTemplates(null);
    }

    @Test
    @DisplayName("Templates endpoint forwards tag filter")
    void getSavedSearchTemplatesShouldForwardTagFilter() throws Exception {
        when(savedSearchService.listBuiltInTemplates("governance")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search/saved/templates").param("tag", "governance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        verify(savedSearchService).listBuiltInTemplates("governance");
    }
}
