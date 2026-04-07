package com.ecm.core.service;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.entity.RuleAction;
import com.ecm.core.entity.RuleCondition;
import com.ecm.core.repository.AutomationRuleRepository;
import com.ecm.core.repository.CategoryRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceFolderScopeTest {

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
    private SecurityService securityService;

    @Mock
    private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @InjectMocks
    private RuleEngineService ruleEngineService;

    @Test
    @DisplayName("Get rules by scope folder delegates to repository")
    void getRulesByScopeFolderShouldDelegateToRepository() {
        UUID folderId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        AutomationRule scopedRule = buildRule("folder-rule", 100, folderId, RuleAction.addTag("a"));
        when(ruleRepository.findByScopeFolderIdActive(folderId, pageable))
            .thenReturn(new PageImpl<>(List.of(scopedRule), pageable, 1));

        var result = ruleEngineService.getRulesByScopeFolder(folderId, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("folder-rule", result.getContent().get(0).getName());
    }

    @Test
    @DisplayName("Reorder scope folder rules assigns sequential priorities")
    void reorderRulesByScopeFolderShouldAssignSequentialPriorities() {
        UUID folderId = UUID.randomUUID();
        UUID ruleAId = UUID.randomUUID();
        UUID ruleBId = UUID.randomUUID();
        UUID ruleCId = UUID.randomUUID();

        AutomationRule ruleA = buildRule("A", 10, folderId, RuleAction.addTag("x"));
        ruleA.setId(ruleAId);
        AutomationRule ruleB = buildRule("B", 20, folderId, RuleAction.addTag("y"));
        ruleB.setId(ruleBId);
        AutomationRule ruleC = buildRule("C", 30, folderId, RuleAction.addTag("z"));
        ruleC.setId(ruleCId);

        when(ruleRepository.findByScopeFolderIdActiveOrderByPriority(folderId))
            .thenReturn(List.of(ruleA, ruleB, ruleC));
        when(ruleRepository.saveAll(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<AutomationRule> reordered = ruleEngineService.reorderRulesByScopeFolder(
            folderId,
            new RuleEngineService.FolderRuleReorderRequest(
                List.of(ruleCId, ruleAId),
                100,
                5
            )
        );

        assertEquals(List.of(ruleCId, ruleAId, ruleBId), reordered.stream().map(AutomationRule::getId).toList());
        assertEquals(List.of(100, 105, 110), reordered.stream().map(AutomationRule::getPriority).toList());

        ArgumentCaptor<List<AutomationRule>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(savedCaptor.capture());
        assertEquals(3, savedCaptor.getValue().size());
    }

    @Test
    @DisplayName("Scope folder dry-run treats script actions as processable")
    void dryRunRulesByScopeFolderShouldReportSummaryAndReasons() {
        UUID folderId = UUID.randomUUID();
        AutomationRule matchedProcessable = buildRule(
            "processable-rule",
            100,
            folderId,
            RuleAction.addTag("important")
        );
        AutomationRule matchedScript = buildRule(
            "script-rule",
            110,
            folderId,
            RuleAction.builder()
                .type(RuleAction.ActionType.EXECUTE_SCRIPT)
                .params(Map.of(
                    RuleAction.ParamKeys.SCRIPT, "documentName.toUpperCase()",
                    RuleAction.ParamKeys.OUTPUT_PROPERTY, "scriptOutput"
                ))
                .build()
        );

        when(ruleRepository.findByScopeFolderIdActiveOrderByPriority(folderId))
            .thenReturn(List.of(matchedProcessable, matchedScript));

        RuleEngineService.FolderRuleDryRunResult result = ruleEngineService.dryRunRulesByScopeFolder(
            folderId,
            new RuleEngineService.FolderRuleDryRunRequest(
                AutomationRule.TriggerType.DOCUMENT_CREATED,
                Map.of(
                    "name", "dry-run.pdf",
                    "mimeType", "application/pdf",
                    "size", 2048
                ),
                50
            )
        );

        assertEquals(2, result.found());
        assertEquals(2, result.scanned());
        assertEquals(2, result.matched());
        assertEquals(2, result.processable());
        assertEquals(0, result.skipped());
        assertEquals(0, result.errors());
        assertTrue(result.skipReasons() == null || result.skipReasons().isEmpty());
        assertTrue(result.results().stream().allMatch(item -> item.processable()));
    }

    private AutomationRule buildRule(String name, int priority, UUID folderId, RuleAction action) {
        return AutomationRule.builder()
            .name(name)
            .description(name + "-desc")
            .triggerType(AutomationRule.TriggerType.DOCUMENT_CREATED)
            .condition(RuleCondition.alwaysTrue())
            .actions(List.of(action))
            .priority(priority)
            .enabled(true)
            .owner("admin")
            .scopeFolderId(folderId)
            .build();
    }
}
