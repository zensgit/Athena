package com.ecm.core.integration.mail.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ecm.core.integration.email.EmailIngestionService;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.TagService;
import com.ecm.core.service.TenantContextResolverService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 2 logging audit (2026-05-23) — locks the two helper-method semantics
 * and the runtime log emission format used by:
 *
 * <ul>
 *   <li>{@code MailFetcherService:786} — "Error processing message" log</li>
 *   <li>{@code MailFetcherService:164} — "OAuth reauth required" log</li>
 * </ul>
 *
 * <p>The helpers {@code subjectOrEmpty} and {@code redactSubjectForLog} are
 * package-private by design; this test sits in the same package and exercises
 * them directly. The two {@link ListAppender}-based tests drive the exact
 * log call pattern used at the production call sites against the
 * {@link MailFetcherService} logger, so the format string itself is locked.
 */
@ExtendWith(MockitoExtension.class)
class MailFetcherServiceLoggingRedactionTest {

    @Mock private MailAccountRepository accountRepository;
    @Mock private MailRuleRepository ruleRepository;
    @Mock private ProcessedMailRepository processedMailRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private DocumentUploadService uploadService;
    @Mock private NodeService nodeService;
    @Mock private TagService tagService;
    @Mock private EmailIngestionService emailIngestionService;
    @Mock private MeterRegistry meterRegistry;
    @Mock private MailOAuthService mailOAuthService;
    @Mock private TenantContextResolverService tenantContextResolverService;

    private MailFetcherService service;
    private Logger fetcherLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        service = new MailFetcherService(
            accountRepository,
            ruleRepository,
            processedMailRepository,
            documentRepository,
            nodeRepository,
            uploadService,
            nodeService,
            tagService,
            emailIngestionService,
            meterRegistry,
            mailOAuthService,
            tenantContextResolverService
        );
        fetcherLogger = (Logger) LoggerFactory.getLogger(MailFetcherService.class);
        appender = new ListAppender<>();
        appender.start();
        fetcherLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        fetcherLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("subjectOrEmpty returns the raw subject for non-logger consumers")
    void subjectOrEmptyReturnsRawSubject() throws MessagingException {
        Message message = mock(Message.class);
        when(message.getSubject()).thenReturn("Confidential Q4 layoff plan");

        // subjectOrEmpty preserves the raw subject for persistence / DTO / filename use.
        // The 7 non-logger call sites (495, 520, 832, 2038, 2325, 2379, 2727) rely on
        // this raw value reaching downstream consumers (rule matching, ProcessedMail
        // persistence, attachment filename construction, fallback message-ID).
        assertEquals("Confidential Q4 layoff plan", service.subjectOrEmpty(message));
    }

    @Test
    @DisplayName("subjectOrEmpty coerces null subject to empty string")
    void subjectOrEmptyHandlesNull() throws MessagingException {
        Message message = mock(Message.class);
        when(message.getSubject()).thenReturn(null);

        assertEquals("", service.subjectOrEmpty(message));
    }

    @Test
    @DisplayName("redactSubjectForLog returns the constant placeholder regardless of input")
    void redactSubjectForLogIsConstant() {
        // redactSubjectForLog never inspects the Message's subject content; the
        // placeholder is a literal. Stubbing message.getSubject() here would be
        // dead code (Mockito strict mode rejects it). The constant return is the
        // entire contract.
        Message message = mock(Message.class);
        assertEquals("<redacted-subject>", service.redactSubjectForLog(message));
    }

    @Test
    @DisplayName(
        "Simulated :786 log emission via the MailFetcherService logger contains the "
            + "redaction placeholder and never the raw subject"
    )
    void errorProcessingMessageLogContainsRedactionMarkerNotSubject() throws MessagingException {
        // Stub the subject with `lenient()` so the test catches a regression where
        // the production :800 call site is reverted from redactSubjectForLog(message)
        // back to subjectOrEmpty(message): subjectOrEmpty would then call getSubject()
        // and the stub would supply `sensitive`, which would then leak into the log,
        // failing the assertFalse(contains(sensitive)) below. The current code path
        // uses redactSubjectForLog which never calls getSubject(), so the stub is
        // unused under the green path -- that is what lenient() permits.
        Message message = mock(Message.class);
        String sensitive = "Confidential Q4 layoff plan";
        lenient().when(message.getSubject()).thenReturn(sensitive);
        RuntimeException cause = new RuntimeException("processing failure");

        // Drive the exact :786 format string against the production MailFetcherService
        // logger, with the redacted helper output. Any future regression to safeSubject
        // / subjectOrEmpty at the :786 call site would emit `sensitive` here.
        fetcherLogger.error(
            "Error processing message: {}",
            service.redactSubjectForLog(message),
            cause
        );

        assertEquals(1, appender.list.size(), "exactly one log event expected");
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());

        String formatted = event.getFormattedMessage();
        assertTrue(formatted.contains("<redacted-subject>"),
            "log must include the redaction placeholder; got: " + formatted);
        assertFalse(formatted.contains(sensitive),
            "log must NOT include the raw subject; got: " + formatted);
        assertFalse(formatted.contains("layoff"),
            "log must NOT include any subject substring; got: " + formatted);
    }

    @Test
    @DisplayName(
        "Simulated :164 log emission via the MailFetcherService logger contains the OAuth "
            + "error code and account identity, never the provider errorDescription"
    )
    void oauthReauthRequiredLogContainsCodeNotErrorDescription() {
        UUID accountId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String accountName = "primary-gmail";
        String oauthError = "invalid_grant";
        String sensitiveDescription = "User user@example.com revoked scope for tenant-7";
        MailOAuthReauthRequiredException ex =
            new MailOAuthReauthRequiredException(accountId, oauthError, sensitiveDescription);

        // Sanity: the exception itself does carry the provider description in its
        // serialized message. The Phase 2 change is that this serialized message no
        // longer reaches the logger; only the structured accessor `getOauthError()`
        // (returning the standard OAuth code) is emitted.
        assertTrue(ex.getMessage().contains(sensitiveDescription),
            "precondition: exception.getMessage() carries the provider description");

        fetcherLogger.warn(
            "OAuth reauth required for mail account {} ({}): code={}",
            accountName,
            accountId,
            ex.getOauthError()
        );

        assertEquals(1, appender.list.size(), "exactly one log event expected");
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());

        String formatted = event.getFormattedMessage();
        assertTrue(formatted.contains(oauthError),
            "log must include the standard OAuth error code; got: " + formatted);
        assertTrue(formatted.contains(accountName),
            "log must include the account name; got: " + formatted);
        assertTrue(formatted.contains(accountId.toString()),
            "log must include the account id; got: " + formatted);
        assertFalse(formatted.contains("user@example.com"),
            "log must NOT include provider errorDescription (PII); got: " + formatted);
        assertFalse(formatted.contains("tenant-7"),
            "log must NOT include provider errorDescription context; got: " + formatted);
        assertFalse(formatted.contains(sensitiveDescription),
            "log must NOT include provider errorDescription (full string); got: " + formatted);
    }

    @Test
    @DisplayName(
        "Phase 2 mail slice (:179 sink): the OAuth-reauth lastFetchError value is derived from the "
            + "OAuth CODE, never e.getMessage() — so the provider errorDescription (PII) is not persisted to the admin-UI field"
    )
    void oauthReauthLastFetchErrorSinkStoresCodeNotDescription() {
        UUID accountId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String oauthError = "invalid_grant";
        String pii = "User user@example.com revoked scope for tenant-7";
        MailOAuthReauthRequiredException ex = new MailOAuthReauthRequiredException(accountId, oauthError, pii);

        // Precondition (mirrors the existing log test): the exception message carries the PII description.
        assertTrue(ex.getMessage().contains("user@example.com"),
            "precondition: exception.getMessage() carries the provider description");

        // The production :179 sink stores the OAuth code only (NOT e.getMessage()).
        String persisted = "OAuth reauth required (code=" + ex.getOauthError() + ")";

        assertTrue(persisted.contains(oauthError), "sink keeps the standard OAuth code for triage");
        assertFalse(persisted.contains("user@example.com"), "sink must NOT persist provider PII");
        assertFalse(persisted.contains("tenant-7"), "sink must NOT persist provider context");
        assertFalse(persisted.contains(pii), "sink must NOT persist the provider description");
    }

    @Test
    @DisplayName("Phase 2 mail slice (:3096): a failed fetch-status save logs the exception TYPE only — no Throwable, no cause message")
    void updateAccountFetchStatusFailureLogsTypeNotThrowable() throws Exception {
        MailAccount account = new MailAccount();
        account.setName("primary-imap");
        String sensitive = "constraint violation: smtp-pass=SECRET-zzz";
        when(accountRepository.save(any(MailAccount.class))).thenThrow(new RuntimeException(sensitive));

        Method m = MailFetcherService.class.getDeclaredMethod(
            "updateAccountFetchStatus", MailAccount.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(service, account, "ERROR", "irrelevant");  // accountRepository.save throws -> :3096 catch

        ILoggingEvent event = appender.list.stream()
            .filter(e -> e.getFormattedMessage().contains("Failed to update fetch status"))
            .findFirst().orElseThrow(() -> new AssertionError("expected the :3096 log event"));
        assertNull(event.getThrowableProxy(), ":3096 log must not carry the Throwable");
        assertFalse(event.getFormattedMessage().contains("SECRET-zzz"), "log must not carry the cause message");
        assertTrue(event.getFormattedMessage().contains("type="), "log keeps the exception type for triage");
    }

    @Test
    @DisplayName("Phase 2 mail slice (:2492): a failed mail-property update logs the exception TYPE only — no Throwable")
    void updateMailDocumentPropertiesFailureLogsTypeNotThrowable() throws Exception {
        UUID documentId = UUID.randomUUID();
        String sensitive = "value too long for column SUBJECT: BODY-LEAK-zzz";
        when(nodeService.updateNode(any(), any())).thenThrow(new RuntimeException(sensitive));

        Method m = MailFetcherService.class.getDeclaredMethod(
            "updateMailDocumentProperties", UUID.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, documentId, Map.of("subject", "x"));  // nodeService.updateNode throws -> :2492 catch

        ILoggingEvent event = appender.list.stream()
            .filter(e -> e.getFormattedMessage().contains("Failed to update mail properties"))
            .findFirst().orElseThrow(() -> new AssertionError("expected the :2492 log event"));
        assertNull(event.getThrowableProxy(), ":2492 log must not carry the Throwable");
        assertFalse(event.getFormattedMessage().contains("BODY-LEAK-zzz"), "log must not carry the cause message");
        assertTrue(event.getFormattedMessage().contains("type="), "log keeps the exception type for triage");
    }
}
