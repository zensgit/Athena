package com.ecm.core.service;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.RuleCondition;
import com.ecm.core.repository.AutomationRuleRepository;
import com.ecm.core.repository.CategoryRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceExecutionLedgerTest {

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
    private NodeService nodeService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private RuleEngineService ruleEngineService;

    @org.junit.jupiter.api.BeforeEach
    void setupInjectables() {
        ReflectionTestUtils.setField(ruleEngineService, "nodeService", nodeService);
        ReflectionTestUtils.setField(ruleEngineService, "securityService", securityService);
        ReflectionTestUtils.setField(ruleEngineService, "auditService", auditService);
    }

    @Test
    @DisplayName("Manual execution uses idempotency key to reuse previous run")
    void executeRuleManualShouldReuseRunByIdempotencyKey() {
        UUID ruleId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AutomationRule rule = buildRule(ruleId, AutomationRule.TriggerType.DOCUMENT_UPDATED);
        Document document = buildDocument(documentId, "contract.pdf");

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(nodeService.getNode(documentId)).thenReturn(document);
        when(ruleRepository.incrementExecutionCount(ruleId)).thenReturn(1);
        when(securityService.getCurrentUser()).thenReturn("admin");

        RuleEngineService.RuleExecutionCommandResult first = ruleEngineService.executeRuleManual(
            ruleId,
            documentId,
            AutomationRule.TriggerType.DOCUMENT_UPDATED,
            "idem-42"
        );
        RuleEngineService.RuleExecutionCommandResult second = ruleEngineService.executeRuleManual(
            ruleId,
            documentId,
            AutomationRule.TriggerType.DOCUMENT_UPDATED,
            "idem-42"
        );

        assertFalse(first.deduplicated());
        assertTrue(second.deduplicated());
        assertEquals(first.runId(), second.runId());
        assertNotNull(second.run());
        assertEquals("admin", first.run().executedBy());
        verify(ruleRepository, times(1)).incrementExecutionCount(ruleId);
    }

    @Test
    @DisplayName("Manual execution returns not-matched run when trigger mismatches")
    void executeRuleManualShouldReturnNotMatchedOnTriggerMismatch() {
        UUID ruleId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AutomationRule rule = buildRule(ruleId, AutomationRule.TriggerType.DOCUMENT_CREATED);
        Document document = buildDocument(documentId, "invoice.pdf");

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(nodeService.getNode(documentId)).thenReturn(document);
        when(securityService.getCurrentUser()).thenReturn("admin");

        RuleEngineService.RuleExecutionCommandResult result = ruleEngineService.executeRuleManual(
            ruleId,
            documentId,
            AutomationRule.TriggerType.DOCUMENT_UPDATED,
            null
        );

        assertFalse(result.deduplicated());
        assertFalse(result.run().conditionMatched());
        assertTrue(result.run().success());
        assertEquals(0, result.run().totalActions());
    }

    @Test
    @DisplayName("Manual execution ledger list supports ruleId filter")
    void listRuleRunsShouldSupportRuleFilter() {
        UUID ruleAId = UUID.randomUUID();
        UUID ruleBId = UUID.randomUUID();
        UUID docAId = UUID.randomUUID();
        UUID docBId = UUID.randomUUID();

        when(ruleRepository.findById(ruleAId)).thenReturn(Optional.of(buildRule(ruleAId, AutomationRule.TriggerType.DOCUMENT_UPDATED)));
        when(ruleRepository.findById(ruleBId)).thenReturn(Optional.of(buildRule(ruleBId, AutomationRule.TriggerType.DOCUMENT_UPDATED)));
        when(nodeService.getNode(docAId)).thenReturn(buildDocument(docAId, "a.pdf"));
        when(nodeService.getNode(docBId)).thenReturn(buildDocument(docBId, "b.pdf"));
        when(ruleRepository.incrementExecutionCount(any())).thenReturn(1);
        when(securityService.getCurrentUser()).thenReturn("admin");

        ruleEngineService.executeRuleManual(ruleAId, docAId, AutomationRule.TriggerType.DOCUMENT_UPDATED, "idem-a");
        ruleEngineService.executeRuleManual(ruleBId, docBId, AutomationRule.TriggerType.DOCUMENT_UPDATED, "idem-b");

        List<RuleEngineService.RuleRunLedgerRecord> filtered = ruleEngineService.listRuleRuns(ruleAId, 20);
        assertEquals(1, filtered.size());
        assertEquals(ruleAId, filtered.get(0).ruleId());
    }

    @Test
    @DisplayName("Manual execution timeline supports actor and time filters")
    void listRuleRunsShouldSupportActorAndTimeFilters() {
        UUID ruleId = UUID.randomUUID();
        UUID firstDocId = UUID.randomUUID();
        UUID secondDocId = UUID.randomUUID();
        AutomationRule rule = buildRule(ruleId, AutomationRule.TriggerType.DOCUMENT_UPDATED);

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(nodeService.getNode(firstDocId)).thenReturn(buildDocument(firstDocId, "first.pdf"));
        when(nodeService.getNode(secondDocId)).thenReturn(buildDocument(secondDocId, "second.pdf"));
        when(ruleRepository.incrementExecutionCount(any())).thenReturn(1);
        when(securityService.getCurrentUser()).thenReturn("alice-admin");
        ruleEngineService.executeRuleManual(ruleId, firstDocId, AutomationRule.TriggerType.DOCUMENT_UPDATED, "idem-1");

        when(securityService.getCurrentUser()).thenReturn("bob-admin");
        ruleEngineService.executeRuleManual(ruleId, secondDocId, AutomationRule.TriggerType.DOCUMENT_UPDATED, "idem-2");

        List<RuleEngineService.RuleRunLedgerRecord> actorFiltered = ruleEngineService.listRuleRuns(
            new RuleEngineService.RuleRunTimelineQuery(
                ruleId,
                null,
                null,
                null,
                "alice",
                null,
                null,
                20
            )
        );
        assertEquals(1, actorFiltered.size());
        assertEquals("alice-admin", actorFiltered.get(0).executedBy());

        List<RuleEngineService.RuleRunLedgerRecord> futureFiltered = ruleEngineService.listRuleRuns(
            new RuleEngineService.RuleRunTimelineQuery(
                ruleId,
                null,
                null,
                null,
                null,
                LocalDateTime.now().plusMinutes(1),
                null,
                20
            )
        );
        assertTrue(futureFiltered.isEmpty());
    }

    private AutomationRule buildRule(UUID ruleId, AutomationRule.TriggerType triggerType) {
        AutomationRule rule = AutomationRule.builder()
            .name("rule-" + ruleId.toString().substring(0, 8))
            .description("manual-run")
            .triggerType(triggerType)
            .condition(RuleCondition.alwaysTrue())
            .actions(List.of())
            .priority(100)
            .enabled(true)
            .owner("admin")
            .build();
        rule.setId(ruleId);
        return rule;
    }

    private Document buildDocument(UUID documentId, String name) {
        Folder parent = new Folder();
        parent.setId(UUID.randomUUID());
        parent.setName("docs");
        parent.setPath("/docs");

        Document document = new Document();
        document.setId(documentId);
        document.setName(name);
        document.setMimeType("application/pdf");
        document.setPath("/docs/" + name);
        document.setParent(parent);
        return document;
    }
}
