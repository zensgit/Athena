package com.ecm.core.service;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.entity.RuleAction;
import com.ecm.core.entity.RuleCondition;
import com.ecm.core.exception.ResourceNotFoundException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceTenantScopeTest {

    @Mock private AutomationRuleRepository ruleRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private TagRepository tagRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        ruleEngineService = new RuleEngineService(
            ruleRepository,
            nodeRepository,
            tagRepository,
            categoryRepository,
            folderRepository,
            tenantWorkspaceScopeService
        );
    }

    @Test
    @DisplayName("createRule defaults missing scope folder to current tenant root")
    void createRuleDefaultsScopeFolderToCurrentTenantRoot() {
        UUID tenantRootId = UUID.randomUUID();
        when(ruleRepository.findByName("Tenant scoped rule")).thenReturn(Optional.empty());
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.resolveCurrentTenantRootNodeId()).thenReturn(tenantRootId);
        when(tenantWorkspaceScopeService.isNodeVisible(tenantRootId)).thenReturn(true);
        when(ruleRepository.save(any(AutomationRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationRule saved = ruleEngineService.createRule(
            RuleEngineService.CreateRuleRequest.builder()
                .name("Tenant scoped rule")
                .description("Tenant scoped rule")
                .triggerType(AutomationRule.TriggerType.DOCUMENT_CREATED)
                .condition(RuleCondition.alwaysTrue())
                .actions(List.of(RuleAction.addTag("tenant")))
                .owner("alice")
                .enabled(true)
                .build()
        );

        assertEquals(tenantRootId, saved.getScopeFolderId());
        ArgumentCaptor<AutomationRule> captor = ArgumentCaptor.forClass(AutomationRule.class);
        verify(ruleRepository).save(captor.capture());
        assertEquals(tenantRootId, captor.getValue().getScopeFolderId());
    }

    @Test
    @DisplayName("getAllRules filters out rules outside current tenant workspace")
    void getAllRulesFiltersOutOfScopeRules() {
        UUID visibleFolderId = UUID.randomUUID();
        UUID hiddenFolderId = UUID.randomUUID();
        AutomationRule visibleRule = buildRule("visible", visibleFolderId);
        AutomationRule hiddenRule = buildRule("hidden", hiddenFolderId);
        AutomationRule globalRule = buildRule("global", null);
        when(ruleRepository.findAllActive(Pageable.unpaged()))
            .thenReturn(new PageImpl<>(List.of(visibleRule, hiddenRule, globalRule)));
        when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Acme Workspace [acme]");
        when(tenantWorkspaceScopeService.isNodeVisible(visibleFolderId, "/Acme Workspace [acme]")).thenReturn(true);
        when(tenantWorkspaceScopeService.isNodeVisible(hiddenFolderId, "/Acme Workspace [acme]")).thenReturn(false);

        var result = ruleEngineService.getAllRules(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("visible", result.getContent().get(0).getName());
    }

    @Test
    @DisplayName("getRule hides rules outside current tenant workspace")
    void getRuleHidesOutOfScopeRule() {
        UUID ruleId = UUID.randomUUID();
        UUID hiddenFolderId = UUID.randomUUID();
        AutomationRule hiddenRule = buildRule("hidden", hiddenFolderId);
        hiddenRule.setId(ruleId);
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(hiddenRule));
        when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Acme Workspace [acme]");
        when(tenantWorkspaceScopeService.isNodeVisible(hiddenFolderId, "/Acme Workspace [acme]")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> ruleEngineService.getRule(ruleId));
    }

    private AutomationRule buildRule(String name, UUID scopeFolderId) {
        return AutomationRule.builder()
            .name(name)
            .description(name)
            .triggerType(AutomationRule.TriggerType.DOCUMENT_CREATED)
            .condition(RuleCondition.alwaysTrue())
            .actions(List.of(RuleAction.addTag("tenant")))
            .enabled(true)
            .owner("alice")
            .scopeFolderId(scopeFolderId)
            .build();
    }
}
