package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.RuleAction;
import com.ecm.core.entity.RuleExecutionResult.ActionExecutionResult;
import com.ecm.core.repository.AutomationRuleRepository;
import com.ecm.core.repository.CategoryRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceTemplateScriptActionTest {

    @Mock
    private AutomationRuleRepository ruleRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private ScriptService scriptService;

    @Mock
    private TemplateService templateService;

    @InjectMocks
    private RuleEngineService ruleEngineService;

    @BeforeEach
    void wireAutomationServices() {
        ReflectionTestUtils.setField(ruleEngineService, "scriptService", scriptService);
        ReflectionTestUtils.setField(ruleEngineService, "templateService", templateService);
    }

    @Test
    @DisplayName("EXECUTE_SCRIPT writes result and logs into document metadata")
    void executeScriptActionWritesMetadata() {
        Document document = buildDocument();
        RuleAction action = RuleAction.builder()
            .type(RuleAction.ActionType.EXECUTE_SCRIPT)
            .params(Map.of(
                RuleAction.ParamKeys.SCRIPT_PATH, "rules/derive-review.js",
                RuleAction.ParamKeys.OUTPUT_PROPERTY, "reviewMetadata",
                RuleAction.ParamKeys.TIMEOUT_MS, 1500
            ))
            .build();

        when(scriptService.executeScriptForAutomation(any()))
            .thenReturn(new ScriptService.ScriptExecutionResult(
                Map.of("bucket", "finance", "confidence", 0.92),
                List.of("INFO: reviewed"),
                "rules/derive-review.js",
                true,
                18,
                LocalDateTime.now()
            ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ScriptService.ScriptExecutionRequest> requestCaptor =
            ArgumentCaptor.forClass(ScriptService.ScriptExecutionRequest.class);

        ActionExecutionResult result = ReflectionTestUtils.invokeMethod(
            ruleEngineService,
            "executeAction",
            action,
            document
        );

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getDetails().contains("metadata.reviewMetadata"));
        assertInstanceOf(Map.class, document.getMetadata().get("reviewMetadata"));
        assertEquals(List.of("INFO: reviewed"), document.getMetadata().get("reviewMetadataLogs"));
        verify(scriptService).executeScriptForAutomation(requestCaptor.capture());
        assertEquals("contract.pdf", requestCaptor.getValue().model().get("documentName"));
        verify(nodeRepository).save(document);
    }

    @Test
    @DisplayName("RENDER_TEMPLATE writes rendered output into document metadata")
    void executeTemplateActionWritesMetadata() {
        Document document = buildDocument();
        RuleAction action = RuleAction.builder()
            .type(RuleAction.ActionType.RENDER_TEMPLATE)
            .params(Map.of(
                RuleAction.ParamKeys.TEMPLATE_PATH, "rules/document-summary.ftl",
                RuleAction.ParamKeys.OUTPUT_PROPERTY, "renderedSummary"
            ))
            .build();

        when(templateService.executeTemplateForAutomation(any()))
            .thenReturn(new TemplateService.TemplateExecutionResult(
                "Document contract.pdf at /docs/contract.pdf",
                "rules/document-summary.ftl",
                true,
                39,
                LocalDateTime.now()
            ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<TemplateService.TemplateExecutionRequest> requestCaptor =
            ArgumentCaptor.forClass(TemplateService.TemplateExecutionRequest.class);

        ActionExecutionResult result = ReflectionTestUtils.invokeMethod(
            ruleEngineService,
            "executeAction",
            action,
            document
        );

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Document contract.pdf at /docs/contract.pdf", document.getMetadata().get("renderedSummary"));
        assertTrue(result.getDetails().contains("metadata.renderedSummary"));
        verify(templateService).executeTemplateForAutomation(requestCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> documentModel = (Map<String, Object>) requestCaptor.getValue().model().get("document");
        assertEquals(document.getPath(), documentModel.get("path"));
        verify(nodeRepository).save(document);
    }

    private Document buildDocument() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("contract.pdf");
        document.setPath("/docs/contract.pdf");
        document.setMimeType("application/pdf");
        document.setMetadata(new LinkedHashMap<>());
        return document;
    }
}
