package com.ecm.core.service;

import com.ecm.core.entity.TemplateDefinition;
import com.ecm.core.repository.TemplateDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateDefinitionRepository templateRepository;
    @Mock private SecurityService securityService;

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService(templateRepository, securityService);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
    }

    @Test
    @DisplayName("createTemplate trims fields and saves FreeMarker template")
    void createTemplateSavesTemplate() {
        when(templateRepository.save(any())).thenAnswer(invocation -> {
            TemplateDefinition template = invocation.getArgument(0);
            template.setId(UUID.randomUUID());
            return template;
        });

        TemplateService.TemplateDefinitionDto saved = templateService.createTemplate(
            new TemplateService.TemplateMutationRequest(
                " Welcome Email ",
                "mail/welcome.ftl",
                " Welcome mail template ",
                "Hello ${user}!",
                List.of("mail", " onboarding ", "mail"),
                true
            )
        );

        assertEquals("Welcome Email", saved.name());
        assertEquals("mail/welcome.ftl", saved.templatePath());
        assertEquals(List.of("mail", "onboarding"), saved.tags());
        assertEquals("FREEMARKER", saved.engine());
    }

    @Test
    @DisplayName("createTemplate rejects duplicate path")
    void createTemplateRejectsDuplicatePath() {
        when(templateRepository.existsByTemplatePath("mail/welcome.ftl")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> templateService.createTemplate(
            new TemplateService.TemplateMutationRequest(
                "Welcome Email",
                "mail/welcome.ftl",
                null,
                "Hello ${user}!",
                null,
                true
            )
        ));
    }

    @Test
    @DisplayName("executeTemplate renders stored template")
    void executeStoredTemplate() {
        TemplateDefinition template = new TemplateDefinition();
        template.setId(UUID.randomUUID());
        template.setName("Welcome");
        template.setTemplatePath("mail/welcome.ftl");
        template.setContent("Hello ${user}! ${site}");
        template.setActive(true);
        when(templateRepository.findByTemplatePathAndActiveTrue("mail/welcome.ftl")).thenReturn(Optional.of(template));

        TemplateService.TemplateExecutionResult result = templateService.executeTemplate(
            new TemplateService.TemplateExecutionRequest(
                "mail/welcome.ftl",
                null,
                Map.of("user", "Athena", "site", "Finance")
            )
        );

        assertTrue(result.storedTemplate());
        assertEquals("mail/welcome.ftl", result.templatePath());
        assertEquals("Hello Athena! Finance", result.rendered());
    }

    @Test
    @DisplayName("executeTemplate renders inline template")
    void executeInlineTemplate() {
        TemplateService.TemplateExecutionResult result = templateService.executeTemplate(
            new TemplateService.TemplateExecutionRequest(
                null,
                "<#list items as item>${item}<#if item_has_next>, </#if></#list>",
                Map.of("items", List.of("one", "two", "three"))
            )
        );

        assertFalse(result.storedTemplate());
        assertNull(result.templatePath());
        assertEquals("one, two, three", result.rendered());
    }

    @Test
    @DisplayName("non-admin cannot manage templates")
    void nonAdminRejected() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        assertThrows(SecurityException.class, () -> templateService.listTemplates());
    }
}
