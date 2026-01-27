package com.ecm.core.service;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.entity.RuleAction;
import com.ecm.core.entity.RuleCondition;
import com.ecm.core.repository.AutomationRuleRepository;
import com.ecm.core.repository.CategoryRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceValidationTest {

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

    @InjectMocks
    private RuleEngineService ruleEngineService;

    @Captor
    private ArgumentCaptor<AutomationRule> ruleCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(ruleRepository.findByName(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void createRule_rejectsNonPositiveManualBackfillMinutes() {
        RuleEngineService.CreateRuleRequest request = buildScheduledRequest(0);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> ruleEngineService.createRule(request)
        );

        assertTrue(error.getMessage().contains("Manual backfill minutes must be between"));
        verify(ruleRepository, never()).save(any(AutomationRule.class));
    }

    @Test
    void createRule_rejectsManualBackfillMinutesAboveMax() {
        RuleEngineService.CreateRuleRequest request = buildScheduledRequest(2000);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> ruleEngineService.createRule(request)
        );

        assertTrue(error.getMessage().contains("Manual backfill minutes must be between"));
        verify(ruleRepository, never()).save(any(AutomationRule.class));
    }

    @Test
    void createRule_acceptsManualBackfillMinutesWithinRange() {
        RuleEngineService.CreateRuleRequest request = buildScheduledRequest(15);
        when(ruleRepository.save(any(AutomationRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationRule saved = ruleEngineService.createRule(request);

        verify(ruleRepository).save(ruleCaptor.capture());
        assertEquals(15, ruleCaptor.getValue().getManualBackfillMinutes());
        assertEquals(15, saved.getManualBackfillMinutes());
    }

    @Test
    void updateRule_rejectsNonPositiveManualBackfillMinutes() {
        UUID ruleId = UUID.randomUUID();
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(buildExistingRule(ruleId, 5)));

        RuleEngineService.UpdateRuleRequest request = buildUpdateRequest(0);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> ruleEngineService.updateRule(ruleId, request)
        );

        assertTrue(error.getMessage().contains("Manual backfill minutes must be between"));
        verify(ruleRepository, never()).save(any(AutomationRule.class));
    }

    @Test
    void updateRule_rejectsManualBackfillMinutesAboveMax() {
        UUID ruleId = UUID.randomUUID();
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(buildExistingRule(ruleId, 5)));

        RuleEngineService.UpdateRuleRequest request = buildUpdateRequest(2000);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> ruleEngineService.updateRule(ruleId, request)
        );

        assertTrue(error.getMessage().contains("Manual backfill minutes must be between"));
        verify(ruleRepository, never()).save(any(AutomationRule.class));
    }

    @Test
    void updateRule_acceptsManualBackfillMinutesWithinRange() {
        UUID ruleId = UUID.randomUUID();
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(buildExistingRule(ruleId, 5)));
        when(ruleRepository.save(any(AutomationRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationRule saved = ruleEngineService.updateRule(ruleId, buildUpdateRequest(30));

        verify(ruleRepository).save(ruleCaptor.capture());
        assertEquals(30, ruleCaptor.getValue().getManualBackfillMinutes());
        assertEquals(30, saved.getManualBackfillMinutes());
    }

    private RuleEngineService.CreateRuleRequest buildScheduledRequest(Integer manualBackfillMinutes) {
        return RuleEngineService.CreateRuleRequest.builder()
            .name("validation-scheduled-rule-" + manualBackfillMinutes)
            .triggerType(AutomationRule.TriggerType.SCHEDULED)
            .condition(RuleCondition.builder().type(RuleCondition.ConditionType.ALWAYS_TRUE).build())
            .actions(List.of(RuleAction.addTag("validation-tag")))
            .cronExpression("0 * * * * *")
            .timezone("UTC")
            .manualBackfillMinutes(manualBackfillMinutes)
            .build();
    }

    private RuleEngineService.UpdateRuleRequest buildUpdateRequest(Integer manualBackfillMinutes) {
        RuleEngineService.UpdateRuleRequest request = new RuleEngineService.UpdateRuleRequest();
        request.setManualBackfillMinutes(manualBackfillMinutes);
        return request;
    }

    private AutomationRule buildExistingRule(UUID ruleId, Integer manualBackfillMinutes) {
        AutomationRule rule = AutomationRule.builder()
            .name("existing-scheduled-rule-" + ruleId)
            .triggerType(AutomationRule.TriggerType.SCHEDULED)
            .condition(RuleCondition.builder().type(RuleCondition.ConditionType.ALWAYS_TRUE).build())
            .actions(List.of(RuleAction.addTag("validation-tag")))
            .cronExpression("0 * * * * *")
            .timezone("UTC")
            .manualBackfillMinutes(manualBackfillMinutes)
            .enabled(true)
            .priority(100)
            .build();
        rule.setId(ruleId);
        return rule;
    }
}
