package com.ecm.core.controller;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.entity.RuleAction;
import com.ecm.core.entity.RuleCondition;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.RuleEngineService;
import com.ecm.core.service.ScheduledRuleRunner;
import com.ecm.core.service.SecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RuleControllerScheduledValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

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
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ruleController)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
        lenient().when(securityService.getCurrentUser()).thenReturn("admin");
    }

    @Test
    @DisplayName("Validate cron returns invalid for schedules below minimum interval")
    void validateCronExpressionReturnsInvalidPayload() throws Exception {
        doThrow(new IllegalArgumentException("Scheduled rules must run at least 5 minutes apart"))
            .when(scheduledRuleRunner)
            .validateCronExpression("0 * * * * *", "UTC");

        mockMvc.perform(post("/api/v1/rules/validate-cron")
                .contentType("application/json")
                .content("""
                    {
                      "cronExpression": "0 * * * * *",
                      "timezone": "UTC"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.error").value("Scheduled rules must run at least 5 minutes apart"));
    }

    @Test
    @DisplayName("Create rule surfaces scheduled validation failures as bad request")
    void createRuleReturnsBadRequestForInvalidScheduledShape() throws Exception {
        doThrow(new IllegalArgumentException("Scheduled rules must run at least 5 minutes apart"))
            .when(ruleEngineService)
            .createRule(any(RuleEngineService.CreateRuleRequest.class));

        String body = objectMapper.writeValueAsString(new RuleController.CreateRuleRequestDto(
            "minute-rule",
            "too frequent",
            AutomationRule.TriggerType.SCHEDULED,
            RuleCondition.alwaysTrue(),
            List.of(RuleAction.addTag("scheduled")),
            100,
            true,
            null,
            null,
            false,
            "0 * * * * *",
            "UTC",
            200,
            15
        ));

        mockMvc.perform(post("/api/v1/rules")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Scheduled rules must run at least 5 minutes apart"))
            .andExpect(jsonPath("$.path").value("/api/v1/rules"));
    }
}
