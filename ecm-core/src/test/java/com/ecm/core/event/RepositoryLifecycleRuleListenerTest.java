package com.ecm.core.event;

import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Version;
import com.ecm.core.service.RuleEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RepositoryLifecycleRuleListenerTest {

    @Mock
    private RuleEngineService ruleEngineService;

    private RepositoryLifecycleRuleListener listener;

    @BeforeEach
    void setUp() {
        listener = new RepositoryLifecycleRuleListener(ruleEngineService);
        ReflectionTestUtils.setField(listener, "rulesEnabled", true);
    }

    @Test
    @DisplayName("dispatches rule execution for document lifecycle event with trigger")
    void dispatchesRuleExecutionForDocumentLifecycleEvent() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("report.docx");

        listener.handleRepositoryLifecycle(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.NODE_UPDATED)
            .node(document)
            .document(document)
            .username("alice")
            .ruleTriggerType(TriggerType.DOCUMENT_UPDATED)
            .build());

        verify(ruleEngineService).evaluateAndExecute(document, TriggerType.DOCUMENT_UPDATED);
    }

    @Test
    @DisplayName("dispatches rule execution for version lifecycle using version document")
    void dispatchesRuleExecutionForVersionLifecycleUsingVersionDocument() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("report.docx");

        Version version = new Version();
        version.setId(UUID.randomUUID());
        version.setDocument(document);

        listener.handleRepositoryLifecycle(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.VERSION_CREATED)
            .version(version)
            .username("alice")
            .ruleTriggerType(TriggerType.VERSION_CREATED)
            .build());

        verify(ruleEngineService).evaluateAndExecute(document, TriggerType.VERSION_CREATED);
    }

    @Test
    @DisplayName("skips dispatch when lifecycle event has no rule trigger")
    void skipsDispatchWhenLifecycleEventHasNoRuleTrigger() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("report.docx");

        listener.handleRepositoryLifecycle(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.NODE_CHECKED_IN)
            .document(document)
            .username("alice")
            .build());

        verify(ruleEngineService, never()).evaluateAndExecute(document, TriggerType.DOCUMENT_UPDATED);
        verify(ruleEngineService, never()).evaluateAndExecute(document, TriggerType.VERSION_CREATED);
    }
}
