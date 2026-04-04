package com.ecm.core.controller;

import com.ecm.core.service.ScriptService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScriptControllerTest {

    @Mock private ScriptService scriptService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ScriptController controller = new ScriptController(scriptService);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /scripts lists managed scripts")
    void listScriptsReturnsScripts() throws Exception {
        when(scriptService.listScripts()).thenReturn(List.of(
            new ScriptService.ScriptDefinitionDto(
                UUID.randomUUID(),
                "Notify Site",
                "scripts/notify-site.js",
                "Notify a site",
                "GRAALJS",
                "({ ok: true })",
                List.of("site"),
                true,
                "admin",
                LocalDateTime.of(2026, 4, 4, 12, 0),
                LocalDateTime.of(2026, 4, 4, 12, 30)
            )
        ));

        mockMvc.perform(get("/api/v1/scripts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Notify Site"))
            .andExpect(jsonPath("$[0].scriptPath").value("scripts/notify-site.js"));
    }

    @Test
    @DisplayName("POST /scripts creates a managed script")
    void createScriptReturnsCreated() throws Exception {
        UUID scriptId = UUID.randomUUID();
        when(scriptService.createScript(any())).thenReturn(
            new ScriptService.ScriptDefinitionDto(
                scriptId,
                "Notify Site",
                "scripts/notify-site.js",
                "Notify a site",
                "GRAALJS",
                "({ ok: true })",
                List.of("site", "notification"),
                true,
                "admin",
                LocalDateTime.of(2026, 4, 4, 12, 0),
                LocalDateTime.of(2026, 4, 4, 12, 0)
            )
        );

        mockMvc.perform(post("/api/v1/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Notify Site",
                      "scriptPath": "scripts/notify-site.js",
                      "description": "Notify a site",
                      "content": "({ ok: true })",
                      "tags": ["site", "notification"],
                      "active": true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(scriptId.toString()))
            .andExpect(jsonPath("$.engine").value("GRAALJS"));
    }

    @Test
    @DisplayName("POST /scripts/execute runs script and returns result")
    void executeScriptReturnsResult() throws Exception {
        when(scriptService.executeScript(any())).thenReturn(
            new ScriptService.ScriptExecutionResult(
                Map.of("ok", true, "count", 2),
                List.of("INFO: finance"),
                "scripts/notify-site.js",
                true,
                8L,
                LocalDateTime.of(2026, 4, 4, 12, 45)
            )
        );

        mockMvc.perform(post("/api/v1/scripts/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "scriptPath", "scripts/notify-site.js",
                    "model", Map.of("site", Map.of("id", "finance"))
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.storedScript").value(true))
            .andExpect(jsonPath("$.result.ok").value(true))
            .andExpect(jsonPath("$.logs[0]").value("INFO: finance"));
    }

    @Test
    @DisplayName("GET /scripts/{id} returns managed script")
    void getScriptReturnsManagedScript() throws Exception {
        UUID scriptId = UUID.randomUUID();
        when(scriptService.getScript(eq(scriptId))).thenReturn(
            new ScriptService.ScriptDefinitionDto(
                scriptId,
                "Cleanup",
                "scripts/cleanup.js",
                null,
                "GRAALJS",
                "true",
                List.of(),
                true,
                "admin",
                LocalDateTime.of(2026, 4, 4, 13, 0),
                LocalDateTime.of(2026, 4, 4, 13, 5)
            )
        );

        mockMvc.perform(get("/api/v1/scripts/{scriptId}", scriptId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scriptPath").value("scripts/cleanup.js"));
    }
}
