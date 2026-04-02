package com.ecm.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecm.core.entity.AutomationRule;
import com.ecm.core.entity.RuleAction;
import com.ecm.core.entity.RuleCondition;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.RuleEngineService;
import com.ecm.core.service.ScheduledRuleRunner;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

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
class RuleControllerFolderScopeTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RuleEngineService ruleEngineService;

    @Mock
    private SecurityService securityService;

    @Mock
    private ScheduledRuleRunner scheduledRuleRunner;

    @Mock
    private AuditService auditService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private RuleController ruleController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(ruleController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("Scoped folder rule listing returns paged rules ordered by priority")
    void getRulesByScopeFolderShouldReturnPagedRules() throws Exception {
        UUID folderId = UUID.randomUUID();
        AutomationRule scopedRule = buildRule("scope-rule-1", 10, folderId);

        when(ruleEngineService.getRulesByScopeFolder(eq(folderId), any()))
            .thenReturn(new PageImpl<>(List.of(scopedRule), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/rules/folders/{folderId}", folderId)
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("scope-rule-1"))
            .andExpect(jsonPath("$.content[0].priority").value(10))
            .andExpect(jsonPath("$.content[0].scopeFolderId").value(folderId.toString()));
    }

    @Test
    @DisplayName("Scoped folder reorder endpoint returns updated rules")
    void reorderScopeFolderRulesShouldReturnUpdatedPayload() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID ruleAId = UUID.randomUUID();
        UUID ruleBId = UUID.randomUUID();

        AutomationRule ruleA = buildRule("rule-A", 100, folderId);
        ruleA.setId(ruleAId);
        AutomationRule ruleB = buildRule("rule-B", 110, folderId);
        ruleB.setId(ruleBId);

        when(ruleEngineService.reorderRulesByScopeFolder(eq(folderId), any()))
            .thenReturn(List.of(ruleA, ruleB));

        String body = objectMapper.writeValueAsString(Map.of(
            "ruleIds", List.of(ruleAId, ruleBId),
            "basePriority", 100,
            "step", 10
        ));

        mockMvc.perform(post("/api/v1/rules/folders/{folderId}/reorder", folderId)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scopeFolderId").value(folderId.toString()))
            .andExpect(jsonPath("$.updated").value(2))
            .andExpect(jsonPath("$.rules.length()").value(2))
            .andExpect(jsonPath("$.rules[0].name").value("rule-A"));
    }

    @Test
    @DisplayName("Scoped folder dry-run returns summary and itemized results")
    void dryRunScopeFolderRulesShouldReturnSummary() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        RuleEngineService.FolderRuleDryRunResult dryRunResult = new RuleEngineService.FolderRuleDryRunResult(
            folderId,
            AutomationRule.TriggerType.DOCUMENT_CREATED,
            3,
            2,
            1,
            1,
            1,
            0,
            Map.of("condition_not_matched", 1L),
            List.of(
                new RuleEngineService.FolderRuleDryRunItem(
                    ruleId,
                    "rule-preview",
                    100,
                    true,
                    true,
                    null,
                    List.of(),
                    null
                )
            )
        );

        when(ruleEngineService.dryRunRulesByScopeFolder(eq(folderId), any()))
            .thenReturn(dryRunResult);

        String body = objectMapper.writeValueAsString(Map.of(
            "triggerType", "DOCUMENT_CREATED",
            "limit", 50,
            "testData", Map.of("name", "dry-run.pdf", "mimeType", "application/pdf")
        ));

        mockMvc.perform(post("/api/v1/rules/folders/{folderId}/dry-run", folderId)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scopeFolderId").value(folderId.toString()))
            .andExpect(jsonPath("$.found").value(3))
            .andExpect(jsonPath("$.scanned").value(2))
            .andExpect(jsonPath("$.matched").value(1))
            .andExpect(jsonPath("$.results.length()").value(1))
            .andExpect(jsonPath("$.results[0].ruleName").value("rule-preview"))
            .andExpect(jsonPath("$.results[0].processable").value(true));
    }

    private AutomationRule buildRule(String name, int priority, UUID scopeFolderId) {
        return AutomationRule.builder()
            .name(name)
            .description(name + "-desc")
            .triggerType(AutomationRule.TriggerType.DOCUMENT_CREATED)
            .condition(RuleCondition.alwaysTrue())
            .actions(List.of(RuleAction.addTag("dry-run")))
            .priority(priority)
            .enabled(true)
            .owner("admin")
            .scopeFolderId(scopeFolderId)
            .build();
    }
}
