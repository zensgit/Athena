package com.ecm.core.controller;

import com.ecm.core.entity.Folder;
import com.ecm.core.service.SavedSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SavedSearchControllerSmartFolderTest {

    @Mock
    private SavedSearchService savedSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SavedSearchController controller = new SavedSearchController(savedSearchService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    @DisplayName("Create smart folder endpoint delegates to service")
    void createSmartFolderDelegatesToService() throws Exception {
        UUID savedSearchId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName("Invoices");
        folder.setPath("/Smart/Invoices");
        folder.setSmart(true);
        folder.setQueryCriteria(Map.of("query", "invoice"));

        when(savedSearchService.createSmartFolder(savedSearchId, "Invoices", "folder from search", parentId))
            .thenReturn(folder);

        mockMvc.perform(post("/api/v1/search/saved/{id}/smart-folder", savedSearchId)
                .contentType("application/json")
                .content("""
                    {
                      "name": "Invoices",
                      "description": "folder from search",
                      "parentId": "%s"
                    }
                    """.formatted(parentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Invoices"))
            .andExpect(jsonPath("$.smart").value(true))
            .andExpect(jsonPath("$.queryCriteria.query").value("invoice"));

        verify(savedSearchService).createSmartFolder(savedSearchId, "Invoices", "folder from search", parentId);
    }
}
