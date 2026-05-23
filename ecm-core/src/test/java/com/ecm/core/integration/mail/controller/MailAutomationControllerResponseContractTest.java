package com.ecm.core.integration.mail.controller;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.model.MailRule;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.integration.mail.service.MailFetcherService;
import com.ecm.core.integration.mail.service.MailOAuthCredentialOwnerAdapter;
import com.ecm.core.integration.mail.service.MailOAuthService;
import com.ecm.core.integration.mail.service.MailProcessedRetentionService;
import com.ecm.core.integration.mail.service.MailReportScheduledExportService;
import com.ecm.core.integration.mail.service.MailReportingService;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.SecurityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MailAutomationControllerResponseContractTest {

    @Mock private MailAccountRepository accountRepository;
    @Mock private MailRuleRepository ruleRepository;
    @Mock private MailFetcherService fetcherService;
    @Mock private MailOAuthCredentialOwnerAdapter oauthOwnerAdapter;
    @Mock private MailOAuthService oauthService;
    @Mock private MailProcessedRetentionService retentionService;
    @Mock private MailReportingService reportingService;
    @Mock private MailReportScheduledExportService scheduledExportService;
    @Mock private ProcessedMailRepository processedMailRepository;
    @Mock private AuditService auditService;
    @Mock private SecurityService securityService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MailAutomationController controller = new MailAutomationController(
            accountRepository,
            ruleRepository,
            fetcherService,
            oauthOwnerAdapter,
            oauthService,
            retentionService,
            reportingService,
            scheduledExportService,
            processedMailRepository,
            auditService,
            securityService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new com.ecm.core.controller.RestExceptionHandler())
            .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("GET /integration/mail/accounts locks MailAccountResponse contract and nullable OAuth/fetch fields")
    void accountsLockMailAccountResponseContract() throws Exception {
        UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MailAccount account = new MailAccount();
        account.setId(accountId);
        account.setName("Tencent Exmail");
        account.setHost("imap.exmail.qq.com");
        account.setPort(993);
        account.setUsername("ops@example.com");
        account.setPassword("encrypted");
        account.setSecurity(MailAccount.SecurityType.SSL);
        account.setEnabled(true);
        account.setPollIntervalMinutes(10);
        account.setOauthProvider(null);
        account.setOauthTokenEndpoint(null);
        account.setOauthTenantId(null);
        account.setOauthScope(null);
        account.setOauthCredentialKey(null);
        account.setLastFetchAt(null);
        account.setLastFetchStatus(null);
        account.setLastFetchError(null);

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(fetcherService.checkOAuthEnv(any(MailAccount.class)))
            .thenReturn(new MailFetcherService.OAuthEnvCheckResult(false, false, "NONE", List.of("MAIL_CLIENT_ID")));

        MvcResult result = mockMvc.perform(get("/api/v1/integration/mail/accounts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(accountId.toString()))
            .andExpect(jsonPath("$[0].security").value("SSL"))
            .andExpect(jsonPath("$[0].oauthProvider", nullValue()))
            .andExpect(jsonPath("$[0].oauthTokenEndpoint", nullValue()))
            .andExpect(jsonPath("$[0].oauthTenantId", nullValue()))
            .andExpect(jsonPath("$[0].oauthScope", nullValue()))
            .andExpect(jsonPath("$[0].oauthCredentialKey", nullValue()))
            .andExpect(jsonPath("$[0].passwordConfigured").value(true))
            .andExpect(jsonPath("$[0].oauthEnvConfigured").value(false))
            .andExpect(jsonPath("$[0].oauthMissingEnvKeys[0]").value("MAIL_CLIENT_ID"))
            .andExpect(jsonPath("$[0].oauthConnected", nullValue()))
            .andExpect(jsonPath("$[0].lastFetchAt", nullValue()))
            .andExpect(jsonPath("$[0].lastFetchStatus", nullValue()))
            .andExpect(jsonPath("$[0].lastFetchError", nullValue()))
            .andReturn();

        JsonNode item = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(mailAccountFieldNames(), fieldNames(item));
    }

    @Test
    @DisplayName("GET /integration/mail/rules locks MailRuleResponse contract and nullable filters/actions")
    void rulesLockMailRuleResponseContract() throws Exception {
        UUID ruleId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID accountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        MailRule rule = new MailRule();
        rule.setId(ruleId);
        rule.setName("Capture invoices");
        rule.setAccountId(accountId);
        rule.setPriority(20);
        rule.setEnabled(true);
        rule.setFolder("INBOX");
        rule.setSubjectFilter("invoice");
        rule.setFromFilter(null);
        rule.setToFilter(null);
        rule.setBodyFilter(null);
        rule.setAttachmentFilenameInclude("*.pdf");
        rule.setAttachmentFilenameExclude(null);
        rule.setMaxAgeDays(null);
        rule.setIncludeInlineAttachments(false);
        rule.setActionType(MailRule.MailActionType.ATTACHMENTS_ONLY);
        rule.setMailAction(MailRule.MailPostAction.MARK_READ);
        rule.setMailActionParam(null);
        rule.setAssignTagId(null);
        rule.setAssignFolderId(null);

        when(ruleRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(rule));

        MvcResult result = mockMvc.perform(get("/api/v1/integration/mail/rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(ruleId.toString()))
            .andExpect(jsonPath("$[0].accountId").value(accountId.toString()))
            .andExpect(jsonPath("$[0].fromFilter", nullValue()))
            .andExpect(jsonPath("$[0].toFilter", nullValue()))
            .andExpect(jsonPath("$[0].bodyFilter", nullValue()))
            .andExpect(jsonPath("$[0].attachmentFilenameExclude", nullValue()))
            .andExpect(jsonPath("$[0].maxAgeDays", nullValue()))
            .andExpect(jsonPath("$[0].mailActionParam", nullValue()))
            .andExpect(jsonPath("$[0].assignTagId", nullValue()))
            .andExpect(jsonPath("$[0].assignFolderId", nullValue()))
            .andReturn();

        JsonNode item = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(mailRuleFieldNames(), fieldNames(item));
    }

    @Test
    @DisplayName("GET /integration/mail/runtime-metrics locks metrics, error stat, and trend contracts")
    void runtimeMetricsLockResponseContract() throws Exception {
        MailFetcherService.MailRuntimeMetrics metrics = new MailFetcherService.MailRuntimeMetrics(
            60,
            12,
            10,
            2,
            0.1667,
            null,
            LocalDateTime.of(2026, 5, 23, 10, 15, 0),
            null,
            "DEGRADED",
            List.of(new MailFetcherService.MailRuntimeErrorStat(
                "Authentication failed",
                2,
                LocalDateTime.of(2026, 5, 23, 10, 30, 0)
            )),
            new MailFetcherService.MailRuntimeTrend(
                "WORSE",
                12,
                8,
                4,
                0.1667,
                0.0,
                0.1667,
                "Error rate increased"
            )
        );
        when(fetcherService.getRuntimeMetrics(60)).thenReturn(metrics);
        when(securityService.getCurrentUser()).thenReturn("admin");

        MvcResult result = mockMvc.perform(get("/api/v1/integration/mail/runtime-metrics")
                .param("windowMinutes", "60"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowMinutes").value(60))
            .andExpect(jsonPath("$.attempts").value(12))
            .andExpect(jsonPath("$.successes").value(10))
            .andExpect(jsonPath("$.errors").value(2))
            .andExpect(jsonPath("$.avgDurationMs", nullValue()))
            .andExpect(jsonPath("$.lastSuccessAt").isNotEmpty())
            .andExpect(jsonPath("$.lastErrorAt", nullValue()))
            .andExpect(jsonPath("$.topErrors[0].errorMessage").value("Authentication failed"))
            .andExpect(jsonPath("$.topErrors[0].lastSeenAt").isNotEmpty())
            .andExpect(jsonPath("$.trend.direction").value("WORSE"))
            .andExpect(jsonPath("$.trend.summary").value("Error rate increased"))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(runtimeMetricsFieldNames(), fieldNames(root));
        assertEquals(runtimeErrorStatFieldNames(), fieldNames(root.get("topErrors").get(0)));
        assertEquals(runtimeTrendFieldNames(), fieldNames(root.get("trend")));
        Mockito.verify(auditService).logEvent(
            Mockito.eq("MAIL_RUNTIME_METRICS_VIEWED"),
            Mockito.isNull(),
            Mockito.eq("MAIL_RUNTIME"),
            Mockito.eq("admin"),
            Mockito.contains("windowMinutes=60")
        );
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> mailAccountFieldNames() {
        return List.of(
            "id",
            "name",
            "host",
            "port",
            "username",
            "security",
            "enabled",
            "pollIntervalMinutes",
            "oauthProvider",
            "oauthTokenEndpoint",
            "oauthTenantId",
            "oauthScope",
            "oauthCredentialKey",
            "passwordConfigured",
            "oauthEnvConfigured",
            "oauthMissingEnvKeys",
            "oauthConnected",
            "lastFetchAt",
            "lastFetchStatus",
            "lastFetchError"
        );
    }

    private static List<String> mailRuleFieldNames() {
        return List.of(
            "id",
            "name",
            "accountId",
            "priority",
            "enabled",
            "folder",
            "subjectFilter",
            "fromFilter",
            "toFilter",
            "bodyFilter",
            "attachmentFilenameInclude",
            "attachmentFilenameExclude",
            "maxAgeDays",
            "includeInlineAttachments",
            "actionType",
            "mailAction",
            "mailActionParam",
            "assignTagId",
            "assignFolderId"
        );
    }

    private static List<String> runtimeMetricsFieldNames() {
        return List.of(
            "windowMinutes",
            "attempts",
            "successes",
            "errors",
            "errorRate",
            "avgDurationMs",
            "lastSuccessAt",
            "lastErrorAt",
            "status",
            "topErrors",
            "trend"
        );
    }

    private static List<String> runtimeErrorStatFieldNames() {
        return List.of("errorMessage", "count", "lastSeenAt");
    }

    private static List<String> runtimeTrendFieldNames() {
        return List.of(
            "direction",
            "currentTotal",
            "previousTotal",
            "deltaTotal",
            "currentErrorRate",
            "previousErrorRate",
            "deltaErrorRate",
            "summary"
        );
    }
}
