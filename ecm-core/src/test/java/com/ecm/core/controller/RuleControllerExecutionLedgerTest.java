package com.ecm.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecm.core.entity.AutomationRule;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.RuleEngineService;
import com.ecm.core.service.ScheduledRuleRunner;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RuleControllerExecutionLedgerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RuleEngineService ruleEngineService;

    @Mock
    private SecurityService securityService;

    @Mock
    private ScheduledRuleRunner scheduledRuleRunner;

    @Mock
    private com.ecm.core.service.AuditService auditService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private RuleController ruleController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(ruleController).build();
    }

    @Test
    @DisplayName("Manual execute endpoint returns run payload with idempotency flags")
    void executeRuleManuallyShouldReturnRunPayload() throws Exception {
        UUID ruleId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        RuleEngineService.RuleRunLedgerRecord run = new RuleEngineService.RuleRunLedgerRecord(
            runId,
            ruleId,
            "auto-tag",
            documentId,
            "invoice.pdf",
            AutomationRule.TriggerType.DOCUMENT_UPDATED,
            "idem-1",
            "admin",
            true,
            true,
            1,
            0,
            1,
            null,
            LocalDateTime.now().minusSeconds(1),
            LocalDateTime.now(),
            120L,
            List.of(
                new RuleEngineService.RuleRunActionRecord(
                    "ADD_TAG",
                    true,
                    null,
                    12L,
                    "tag=important"
                )
            )
        );

        when(ruleEngineService.executeRuleManual(eq(ruleId), eq(documentId), any(), eq("idem-1")))
            .thenReturn(new RuleEngineService.RuleExecutionCommandResult(
                runId,
                false,
                null,
                run
            ));

        String body = objectMapper.writeValueAsString(Map.of(
            "documentId", documentId,
            "triggerType", "DOCUMENT_UPDATED",
            "idempotencyKey", "idem-1"
        ));

        mockMvc.perform(post("/api/v1/rules/{ruleId}/execute", ruleId)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value(runId.toString()))
            .andExpect(jsonPath("$.deduplicated").value(false))
            .andExpect(jsonPath("$.run.ruleName").value("auto-tag"))
            .andExpect(jsonPath("$.run.executedBy").value("admin"))
            .andExpect(jsonPath("$.run.actions.length()").value(1))
            .andExpect(jsonPath("$.run.actions[0].actionType").value("ADD_TAG"));
    }

    @Test
    @DisplayName("Manual execution ledger list endpoint returns recent runs")
    void listManualRuleExecutionsShouldReturnRuns() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        RuleEngineService.RuleRunLedgerRecord run = new RuleEngineService.RuleRunLedgerRecord(
            runId,
            ruleId,
            "auto-tag",
            documentId,
            "invoice.pdf",
            AutomationRule.TriggerType.DOCUMENT_UPDATED,
            null,
            "admin",
            true,
            true,
            1,
            0,
            1,
            null,
            LocalDateTime.now().minusSeconds(1),
            LocalDateTime.now(),
            120L,
            List.of()
        );
        when(ruleEngineService.listRuleRuns(any(RuleEngineService.RuleRunTimelineQuery.class))).thenReturn(List.of(run));

        mockMvc.perform(get("/api/v1/rules/executions")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].runId").value(runId.toString()))
            .andExpect(jsonPath("$[0].ruleName").value("auto-tag"));

        ArgumentCaptor<RuleEngineService.RuleRunTimelineQuery> queryCaptor =
            ArgumentCaptor.forClass(RuleEngineService.RuleRunTimelineQuery.class);
        verify(ruleEngineService).listRuleRuns(queryCaptor.capture());
        RuleEngineService.RuleRunTimelineQuery query = queryCaptor.getValue();
        assertEquals(20, query.limit());
    }

    @Test
    @DisplayName("Manual execution ledger list endpoint binds timeline filters")
    void listManualRuleExecutionsShouldBindTimelineFilters() throws Exception {
        when(ruleEngineService.listRuleRuns(any(RuleEngineService.RuleRunTimelineQuery.class)))
            .thenReturn(List.of());

        UUID ruleId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/rules/executions")
                .param("ruleId", ruleId.toString())
                .param("documentId", documentId.toString())
                .param("triggerType", "DOCUMENT_UPDATED")
                .param("success", "true")
                .param("actor", "admin")
                .param("from", "2026-03-13T10:00:00")
                .param("to", "2026-03-13T12:00:00")
                .param("limit", "15"))
            .andExpect(status().isOk());

        ArgumentCaptor<RuleEngineService.RuleRunTimelineQuery> queryCaptor =
            ArgumentCaptor.forClass(RuleEngineService.RuleRunTimelineQuery.class);
        verify(ruleEngineService).listRuleRuns(queryCaptor.capture());
        RuleEngineService.RuleRunTimelineQuery query = queryCaptor.getValue();
        assertEquals(ruleId, query.ruleId());
        assertEquals(documentId, query.documentId());
        assertEquals(AutomationRule.TriggerType.DOCUMENT_UPDATED, query.triggerType());
        assertEquals(Boolean.TRUE, query.success());
        assertEquals("admin", query.actor());
        assertEquals(15, query.limit());
    }

    @Test
    @DisplayName("Manual execution timeline export endpoint returns CSV attachment")
    void exportManualRuleExecutionsShouldReturnCsv() throws Exception {
        UUID runId = UUID.randomUUID();
        RuleEngineService.RuleRunLedgerRecord run = new RuleEngineService.RuleRunLedgerRecord(
            runId,
            UUID.randomUUID(),
            "auto-tag",
            UUID.randomUUID(),
            "invoice.pdf",
            AutomationRule.TriggerType.DOCUMENT_UPDATED,
            "idem-1",
            "admin",
            true,
            true,
            1,
            0,
            1,
            null,
            LocalDateTime.now().minusSeconds(1),
            LocalDateTime.now(),
            120L,
            List.of()
        );
        when(ruleEngineService.listRuleRuns(any(RuleEngineService.RuleRunTimelineQuery.class))).thenReturn(List.of(run));
        when(securityService.getCurrentUser()).thenReturn("admin");

        mockMvc.perform(get("/api/v1/rules/executions/export")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment;")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("runId,ruleId,ruleName")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(runId.toString())));
    }

    @Test
    @DisplayName("Manual execution ledger get endpoint returns run detail")
    void getManualRuleExecutionShouldReturnRunDetail() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        RuleEngineService.RuleRunLedgerRecord run = new RuleEngineService.RuleRunLedgerRecord(
            runId,
            ruleId,
            "auto-tag",
            documentId,
            "invoice.pdf",
            AutomationRule.TriggerType.DOCUMENT_UPDATED,
            null,
            "admin",
            true,
            true,
            1,
            0,
            1,
            null,
            LocalDateTime.now().minusSeconds(1),
            LocalDateTime.now(),
            120L,
            List.of()
        );
        when(ruleEngineService.getRuleRun(runId)).thenReturn(Optional.of(run));

        mockMvc.perform(get("/api/v1/rules/executions/{runId}", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value(runId.toString()))
            .andExpect(jsonPath("$.documentId").value(documentId.toString()));
    }
}
