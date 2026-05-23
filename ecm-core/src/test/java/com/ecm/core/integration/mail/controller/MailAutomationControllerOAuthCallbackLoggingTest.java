package com.ecm.core.integration.mail.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Phase 2 logging audit (2026-05-23) — locks the runtime log emission shape of
 * {@code MailAutomationController.oauthCallback(...)}'s catch block (line 362).
 *
 * <p>The production change replaces {@code log.warn("OAuth callback failed", ex)}
 * (which passes the full {@link Throwable} to SLF4J — stack and message both emit)
 * with {@code log.warn("OAuth callback failed: type={} (message redacted)",
 * ex.getClass().getSimpleName())} so that no exception message or stack carries
 * the OAuth authorization code or provider-controlled error description into the
 * log sink.
 */
@ExtendWith(MockitoExtension.class)
class MailAutomationControllerOAuthCallbackLoggingTest {

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

    private MailAutomationController controller;
    private Logger controllerLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        controller = new MailAutomationController(
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
        controllerLogger = (Logger) LoggerFactory.getLogger(MailAutomationController.class);
        appender = new ListAppender<>();
        appender.start();
        controllerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        controllerLogger.detachAppender(appender);
    }

    @Test
    @DisplayName(
        "OAuth callback exception emits only the exception class name; no provider message, "
            + "no authorization code, no PII content, no attached Throwable"
    )
    void oauthCallbackExceptionLogsClassNameOnly() {
        String sensitiveCode = "secret_authz_code_xyz123";
        String state = "session_state_token";
        String sensitivePii = "user@example.com tenant-7";
        // Provider error wrapped in a RuntimeException whose message embeds both the
        // OAuth authorization code AND PII-shaped description content. The pre-Phase-2
        // call `log.warn("OAuth callback failed", ex)` would have emitted this entire
        // message string plus full stack trace to the log sink.
        RuntimeException providerFailure = new RuntimeException(
            "provider rejected code=" + sensitiveCode + " for " + sensitivePii
        );
        when(oauthService.handleCallback(sensitiveCode, state)).thenThrow(providerFailure);

        ResponseEntity<Void> response = controller.oauthCallback(sensitiveCode, state);

        // Existing redirect-on-failure behavior must be preserved.
        assertEquals(302, response.getStatusCode().value());

        assertEquals(1, appender.list.size(), "exactly one log event expected");
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());

        String formatted = event.getFormattedMessage();
        assertTrue(formatted.contains("RuntimeException"),
            "log must include the exception class simple name; got: " + formatted);
        assertTrue(formatted.contains("message redacted"),
            "log must include the explicit 'message redacted' marker; got: " + formatted);
        assertFalse(formatted.contains(sensitiveCode),
            "log must NOT include the OAuth authorization code; got: " + formatted);
        assertFalse(formatted.contains("user@example.com"),
            "log must NOT include PII-shaped content from the provider message; got: " + formatted);
        assertFalse(formatted.contains("tenant-7"),
            "log must NOT include provider-controlled context; got: " + formatted);
        assertFalse(formatted.contains("provider rejected"),
            "log must NOT include the exception message body; got: " + formatted);

        // Throwable proxy attached to the log event is the second mechanism by which
        // sensitive exception content could reach the log sink (SLF4J appends the
        // full stack and message when a Throwable is passed as the last varargs).
        // The Phase 2 change drops the Throwable entirely from the log call, so the
        // event must not carry a throwable proxy.
        assertNull(event.getThrowableProxy(),
            "Throwable must NOT be attached to the log event (was attached pre-Phase-2)");
    }
}
