package com.ecm.core.service;

import com.ecm.core.entity.ScriptDefinition;
import com.ecm.core.repository.ScriptDefinitionRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScriptServiceTest {

    @Mock private ScriptDefinitionRepository scriptRepository;
    @Mock private SecurityService securityService;

    private ScriptService scriptService;

    @BeforeEach
    void setUp() {
        scriptService = new ScriptService(scriptRepository, securityService);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
    }

    @Test
    @DisplayName("createScript trims fields and saves GraalJS definition")
    void createScriptSavesDefinition() {
        when(scriptRepository.save(any())).thenAnswer(invocation -> {
            ScriptDefinition definition = invocation.getArgument(0);
            definition.setId(UUID.randomUUID());
            return definition;
        });

        ScriptService.ScriptDefinitionDto saved = scriptService.createScript(
            new ScriptService.ScriptMutationRequest(
                " Notify Site ",
                "scripts/notify-site.js",
                " Site notification script ",
                "logger.info(site.id); ({ ok: true });",
                List.of("site", "notification", "site"),
                true
            )
        );

        assertEquals("Notify Site", saved.name());
        assertEquals("scripts/notify-site.js", saved.scriptPath());
        assertEquals(List.of("site", "notification"), saved.tags());
        assertEquals("GRAALJS", saved.engine());
    }

    @Test
    @DisplayName("executeScript renders stored script result and logs")
    void executeStoredScript() {
        ScriptDefinition script = new ScriptDefinition();
        script.setId(UUID.randomUUID());
        script.setName("Notify Site");
        script.setScriptPath("scripts/notify-site.js");
        script.setContent("logger.info(site.id); ({ status: 'ok', total: documentCount * 2 });");
        script.setActive(true);
        when(scriptRepository.findByScriptPathAndActiveTrue("scripts/notify-site.js")).thenReturn(Optional.of(script));

        ScriptService.ScriptExecutionResult result = scriptService.executeScript(
            new ScriptService.ScriptExecutionRequest(
                "scripts/notify-site.js",
                null,
                Map.of("site", Map.of("id", "finance"), "documentCount", 6),
                2000L
            )
        );

        assertTrue(result.storedScript());
        assertEquals("scripts/notify-site.js", result.scriptPath());
        assertEquals(Map.of("status", "ok", "total", 12), result.result());
        assertEquals(List.of("INFO: finance"), result.logs());
    }

    @Test
    @DisplayName("executeScript supports inline scripts")
    void executeInlineScript() {
        ScriptService.ScriptExecutionResult result = scriptService.executeScript(
            new ScriptService.ScriptExecutionRequest(
                null,
                "const titles = model.items.map(item => item.title); titles.join(', ');",
                Map.of("items", List.of(Map.of("title", "A"), Map.of("title", "B"))),
                2000L
            )
        );

        assertFalse(result.storedScript());
        assertNull(result.scriptPath());
        assertEquals("A, B", result.result());
    }

    @Test
    @DisplayName("executeScript blocks host access")
    void executeScriptBlocksHostAccess() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> scriptService.executeScript(
            new ScriptService.ScriptExecutionRequest(
                null,
                "Java.type('java.lang.System').currentTimeMillis();",
                Map.of(),
                2000L
            )
        ));

        assertTrue(error.getMessage().contains("Script execution failed"));
    }

    @Test
    @DisplayName("non-admin cannot manage scripts")
    void nonAdminRejected() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        assertThrows(SecurityException.class, () -> scriptService.listScripts());
    }
}
