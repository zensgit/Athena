package com.ecm.core.controller;

import com.ecm.core.service.TemplateService;
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
class TemplateControllerTest {

    @Mock private TemplateService templateService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TemplateController controller = new TemplateController(templateService);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /templates lists managed templates")
    void listTemplatesReturnsTemplates() throws Exception {
        when(templateService.listTemplates()).thenReturn(List.of(
            new TemplateService.TemplateDefinitionDto(
                UUID.randomUUID(),
                "Welcome Email",
                "mail/welcome.ftl",
                "Welcome template",
                "FREEMARKER",
                "Hello ${user}",
                List.of("mail"),
                true,
                "admin",
                LocalDateTime.of(2026, 4, 4, 10, 0),
                LocalDateTime.of(2026, 4, 4, 10, 30)
            )
        ));

        mockMvc.perform(get("/api/v1/templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Welcome Email"))
            .andExpect(jsonPath("$[0].templatePath").value("mail/welcome.ftl"));
    }

    @Test
    @DisplayName("POST /templates creates a managed template")
    void createTemplateReturnsCreated() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(templateService.createTemplate(any())).thenReturn(
            new TemplateService.TemplateDefinitionDto(
                templateId,
                "Welcome Email",
                "mail/welcome.ftl",
                "Welcome template",
                "FREEMARKER",
                "Hello ${user}",
                List.of("mail", "welcome"),
                true,
                "admin",
                LocalDateTime.of(2026, 4, 4, 10, 0),
                LocalDateTime.of(2026, 4, 4, 10, 0)
            )
        );

        mockMvc.perform(post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Welcome Email",
                      "templatePath": "mail/welcome.ftl",
                      "description": "Welcome template",
                      "content": "Hello ${user}",
                      "tags": ["mail", "welcome"],
                      "active": true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(templateId.toString()))
            .andExpect(jsonPath("$.engine").value("FREEMARKER"));
    }

    @Test
    @DisplayName("POST /templates/execute renders stored template")
    void executeStoredTemplateReturnsRenderedContent() throws Exception {
        when(templateService.executeTemplate(any())).thenReturn(
            new TemplateService.TemplateExecutionResult(
                "Hello Athena",
                "mail/welcome.ftl",
                true,
                12,
                LocalDateTime.of(2026, 4, 4, 11, 0)
            )
        );

        mockMvc.perform(post("/api/v1/templates/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "templatePath", "mail/welcome.ftl",
                    "model", Map.of("user", "Athena")
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.storedTemplate").value(true))
            .andExpect(jsonPath("$.rendered").value("Hello Athena"));
    }

    @Test
    @DisplayName("GET /templates/{id} returns a managed template")
    void getTemplateReturnsManagedTemplate() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(templateService.getTemplate(eq(templateId))).thenReturn(
            new TemplateService.TemplateDefinitionDto(
                templateId,
                "Invoice Notice",
                "mail/invoice.ftl",
                null,
                "FREEMARKER",
                "Invoice for ${customer}",
                List.of(),
                true,
                "admin",
                LocalDateTime.of(2026, 4, 4, 12, 0),
                LocalDateTime.of(2026, 4, 4, 12, 15)
            )
        );

        mockMvc.perform(get("/api/v1/templates/{templateId}", templateId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.templatePath").value("mail/invoice.ftl"));
    }
}
