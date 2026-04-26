package com.ecm.core.controller;

import com.ecm.core.service.LocalizedContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LocalizedContentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LocalizedContentService localizedContentService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LocalizedContentController(localizedContentService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    // ------------------------------------------------------------------ helper

    private LocalizedContentService.LocalizedContentDto buildDto(UUID nodeId, String locale, String title) {
        return new LocalizedContentService.LocalizedContentDto(
            UUID.randomUUID(),
            nodeId,
            locale,
            title,
            "A description",
            LocalDateTime.of(2026, 4, 26, 9, 0),
            "admin",
            LocalDateTime.of(2026, 4, 26, 10, 0)
        );
    }

    // ------------------------------------------------------------------ list

    @Test
    @DisplayName("listLocalizations returns list of locale entries for a node")
    void listLocalizationsReturnsLocaleEntries() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(localizedContentService.listForNode(nodeId)).thenReturn(List.of(
            buildDto(nodeId, "en", "English Title"),
            buildDto(nodeId, "zh", "Chinese Title")
        ));

        mockMvc.perform(get("/api/v1/nodes/" + nodeId + "/localizations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].locale").value("en"))
            .andExpect(jsonPath("$[0].title").value("English Title"))
            .andExpect(jsonPath("$[1].locale").value("zh"))
            .andExpect(jsonPath("$[1].title").value("Chinese Title"));
    }

    // ------------------------------------------------------------------ upsert

    @Test
    @DisplayName("upsertLocalization returns 200 with updated dto")
    void upsertLocalizationReturnsUpdatedDto() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(localizedContentService.upsert(
            Mockito.eq(nodeId),
            Mockito.eq("en"),
            Mockito.any(LocalizedContentService.LocalizedContentRequest.class)
        )).thenReturn(buildDto(nodeId, "en", "English Title"));

        mockMvc.perform(put("/api/v1/nodes/" + nodeId + "/localizations/en")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "English Title", "description": "A description" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locale").value("en"))
            .andExpect(jsonPath("$.title").value("English Title"))
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()));
    }

    // ------------------------------------------------------------------ delete

    @Test
    @DisplayName("deleteLocalization returns 204 no content")
    void deleteLocalizationReturnsNoContent() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.doNothing().when(localizedContentService).delete(nodeId, "en");

        mockMvc.perform(delete("/api/v1/nodes/" + nodeId + "/localizations/en"))
            .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ resolve

    @Test
    @DisplayName("resolveLocalization returns 200 with best-match dto when Accept-Language matches")
    void resolveLocalizationReturns200WhenMatchFound() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(localizedContentService.resolve(
            Mockito.eq(nodeId),
            Mockito.eq("zh-CN")
        )).thenReturn(Optional.of(buildDto(nodeId, "zh-cn", "Chinese Title")));

        mockMvc.perform(get("/api/v1/nodes/" + nodeId + "/localization")
                .header("Accept-Language", "zh-CN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locale").value("zh-cn"))
            .andExpect(jsonPath("$.title").value("Chinese Title"));
    }

    @Test
    @DisplayName("resolveLocalization returns 404 when no localizations exist")
    void resolveLocalizationReturns404WhenNoLocalizationsExist() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(localizedContentService.resolve(
            Mockito.eq(nodeId),
            Mockito.any()
        )).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/nodes/" + nodeId + "/localization")
                .header("Accept-Language", "fr"))
            .andExpect(status().isNotFound());
    }
}
