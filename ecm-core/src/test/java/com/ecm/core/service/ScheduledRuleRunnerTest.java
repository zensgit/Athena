package com.ecm.core.service;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.repository.AutomationRuleRepository;
import com.ecm.core.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledRuleRunnerTest {

    @Mock
    private AutomationRuleRepository ruleRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private RuleEngineService ruleEngineService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private ScheduledRuleRunner scheduledRuleRunner;

    @Captor
    private ArgumentCaptor<LocalDateTime> sinceCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduledRuleRunner, "manualBackfillMinutes", 5L);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(ruleRepository.updateScheduledRunTimes(any(), any(), any())).thenReturn(1);
        when(documentRepository.findModifiedSince(any(), any(Pageable.class))).thenReturn(Page.empty());
    }

    @Test
    void triggerRule_backfillsRecentWindow_whenLastRunIsTooRecent() {
        LocalDateTime now = LocalDateTime.now();
        AutomationRule rule = buildScheduledRule(now.minusSeconds(10));

        scheduledRuleRunner.triggerRule(rule);

        verify(documentRepository).findModifiedSince(sinceCaptor.capture(), any(Pageable.class));
        LocalDateTime usedSince = sinceCaptor.getValue();
        LocalDateTime backfillFloor = now.minusMinutes(4);

        assertFalse(usedSince.isAfter(backfillFloor), "Manual trigger should backfill a recent window");
        assertTrue(usedSince.isAfter(now.minusHours(1)), "Backfill window should not be overly broad");
    }

    @Test
    void triggerRule_usesLastRunAt_whenItIsOlderThanBackfillWindow() {
        LocalDateTime lastRunAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        AutomationRule rule = buildScheduledRule(lastRunAt);

        scheduledRuleRunner.triggerRule(rule);

        verify(documentRepository).findModifiedSince(sinceCaptor.capture(), any(Pageable.class));
        assertEquals(lastRunAt, sinceCaptor.getValue());
    }

    private AutomationRule buildScheduledRule(LocalDateTime lastRunAt) {
        AutomationRule rule = AutomationRule.builder()
            .name("scheduled-rule-test")
            .triggerType(AutomationRule.TriggerType.SCHEDULED)
            .cronExpression("0 * * * * *")
            .enabled(true)
            .lastRunAt(lastRunAt)
            .build();
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        return rule;
    }
}
