package com.ecm.core.security.mfa;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 2 logging audit slice 2 (finding {@code TotpService:82}). Locks the runtime log shape of the
 * HMAC-failure catch block: the production change replaces {@code log.error("Failed to compute HMAC", e)}
 * (full {@link Throwable} -&gt; SLF4J emits its stack and cause-chain messages — a crypto/key path) with
 * {@code log.error("Failed to compute HMAC: type={}", e.getClass().getSimpleName())} so no exception
 * message or stack can carry key material into the log sink. Mirrors
 * {@code MailAutomationControllerOAuthCallbackLoggingTest}.
 */
class TotpServiceHmacFailureLoggingTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(TotpService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("HMAC failure logs the exception TYPE only — no Throwable attached, so no cause chain / key material reaches the log")
    void hmacFailureLogsTypeWithoutThrowable() throws Exception {
        TotpService service = new TotpService();
        Method generateCode = TotpService.class.getDeclaredMethod("generateCode", String.class, long.class);
        generateCode.setAccessible(true);

        // Empty secret -> empty key -> SecretKeySpec rejects it -> the HMAC catch block (:81-84) fires.
        InvocationTargetException ite = assertThrows(InvocationTargetException.class,
            () -> generateCode.invoke(service, "", 1L));
        assertNotNull(ite.getCause(), "the HMAC failure should surface as a thrown cause");

        ILoggingEvent event = appender.list.stream()
            .filter(e -> e.getFormattedMessage().contains("Failed to compute HMAC"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected an HMAC-failure log event"));

        // The Throwable is NOT attached -> SLF4J cannot walk its cause chain into the log sink.
        assertNull(event.getThrowableProxy(), "HMAC-failure log must not carry the Throwable");
        // The exception type is preserved for triage.
        assertThat(event.getFormattedMessage(), containsString("type="));
    }
}
