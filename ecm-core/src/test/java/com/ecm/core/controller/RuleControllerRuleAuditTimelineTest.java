package com.ecm.core.controller;

import com.ecm.core.entity.AuditLog;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RuleControllerRuleAuditTimelineTest {

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
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(ruleController).build();
    }

    @Test
    @DisplayName("Rule audit timeline endpoint returns filtered audit rows")
    void listRuleExecutionAuditTimelineShouldReturnRows() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
            .id(logId)
            .eventType("RULE_MANUAL_RUN_EXECUTED")
            .nodeId(nodeId)
            .nodeName("invoice.pdf")
            .username("admin")
            .eventTime(LocalDateTime.now())
            .details("manual execution")
            .build();

        when(auditLogRepository.findRuleAuditTimeline(
            eq("RULE_MANUAL_RUN_EXECUTED"),
            eq("admin"),
            eq(nodeId),
            any(),
            any(),
            any()
        )).thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/rules/executions/audit")
                .param("eventType", "RULE_MANUAL_RUN_EXECUTED")
                .param("actor", "admin")
                .param("nodeId", nodeId.toString())
                .param("from", "2026-03-13T10:00:00")
                .param("to", "2026-03-13T12:00:00")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(logId.toString()))
            .andExpect(jsonPath("$[0].eventType").value("RULE_MANUAL_RUN_EXECUTED"))
            .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    @DisplayName("Rule audit timeline export endpoint returns CSV attachment")
    void exportRuleExecutionAuditTimelineShouldReturnCsv() throws Exception {
        UUID logId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
            .id(logId)
            .eventType("RULE_EXECUTED")
            .nodeId(UUID.randomUUID())
            .nodeName("contract.pdf")
            .username("editor")
            .eventTime(LocalDateTime.now())
            .details("rule executed")
            .build();

        when(auditLogRepository.findRuleAuditTimelineNoNodeId(any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 20), 1));
        when(securityService.getCurrentUser()).thenReturn("admin");

        mockMvc.perform(get("/api/v1/rules/executions/audit/export")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment;")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id,eventType,nodeId,nodeName,username,eventTime,details")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(logId.toString())));
    }

    @Test
    @DisplayName("Rule audit timeline omits nodeId predicate when nodeId is absent")
    void listRuleExecutionAuditTimelineShouldUseNoNodeIdQueryWhenNodeIdAbsent() throws Exception {
        when(auditLogRepository.findRuleAuditTimelineNoNodeId(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any()
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        mockMvc.perform(get("/api/v1/rules/executions/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        verify(auditLogRepository).findRuleAuditTimelineNoNodeId(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any()
        );
        verify(auditLogRepository, never()).findRuleAuditTimeline(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rule audit timeline endpoint rejects invalid datetime format")
    void listRuleExecutionAuditTimelineShouldRejectInvalidDatetime() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions/audit")
                .param("from", "invalid-datetime"))
            .andExpect(status().isBadRequest());
    }
}
