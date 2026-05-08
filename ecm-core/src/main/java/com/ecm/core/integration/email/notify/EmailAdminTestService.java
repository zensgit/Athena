package com.ecm.core.integration.email.notify;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Synchronous "test SMTP" service used by the admin diagnostic endpoint.
 *
 * <p>This deliberately bypasses {@link EmailNotificationService} and the
 * {@code ecm.email.enabled} flag — the whole point of this endpoint is letting
 * operators verify {@code spring.mail.*} <em>before</em> enabling real
 * notifications. It also runs synchronously so the operator gets the result
 * inline; we are not deferring failure to a background thread.</p>
 *
 * <p>The service never returns or logs the recipient address as part of the
 * response body, never reads OAuth tokens, and never reads
 * {@link MailAccount}-style credentials. The only configuration it surfaces is
 * the operator-facing {@code spring.mail.host}, {@code spring.mail.port} and
 * {@code ecm.email.from-address} — all of which the operator already controls.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAdminTestService {

    static final String TEST_SUBJECT = "[Athena] SMTP test";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Environment environment;

    /**
     * Attempt to dispatch a single test message to {@code to}. Never throws —
     * all error paths return a populated {@link EmailTestSmtpResult}.
     *
     * @param to recipient email address; must be non-blank and contain '@'.
     * @return immutable result describing dispatch outcome and current SMTP
     *         configuration. {@code diagnostic} is null on success and the
     *         exception message (or class+message for non-MailException) on
     *         failure.
     */
    public EmailTestSmtpResult sendTestMessage(String to) {
        String smtpHost = environment.getProperty("spring.mail.host");
        Integer smtpPort = parsePort(environment.getProperty("spring.mail.port"));
        String fromAddress = environment.getProperty("ecm.email.from-address", "");

        // Order of validation matches the brief: recipient → JavaMailSender →
        // from-address → send. An invalid recipient never touches the mailer.
        if (to == null || to.isBlank() || !to.contains("@")) {
            return EmailTestSmtpResult.failure(
                "Invalid recipient",
                null,
                smtpHost,
                smtpPort,
                fromAddress
            );
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return EmailTestSmtpResult.failure(
                "JavaMailSender not configured",
                "Add spring.mail.host / spring.mail.port to application config to enable SMTP.",
                smtpHost,
                smtpPort,
                fromAddress
            );
        }

        if (fromAddress == null || fromAddress.isBlank()) {
            return EmailTestSmtpResult.failure(
                "ecm.email.from-address not configured",
                null,
                smtpHost,
                smtpPort,
                fromAddress
            );
        }

        String body = "This is a test message from Athena's admin Test SMTP control. "
            + "Configuration: spring.mail.host=" + smtpHost
            + ", spring.mail.port=" + smtpPort + ".";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(TEST_SUBJECT);
            helper.setText(body, false);

            mailSender.send(message);

            log.info(
                "sendTestMessage: dispatched smtpHost={} smtpPort={} fromAddress={}",
                smtpHost,
                smtpPort,
                fromAddress
            );
            return EmailTestSmtpResult.success(smtpHost, smtpPort, fromAddress);
        } catch (MailException ex) {
            log.warn(
                "sendTestMessage: SMTP send failed smtpHost={} smtpPort={} cause={}",
                smtpHost,
                smtpPort,
                ex.getMessage()
            );
            return EmailTestSmtpResult.failure(
                "SMTP send failed",
                ex.getMessage(),
                smtpHost,
                smtpPort,
                fromAddress
            );
        } catch (Exception ex) {
            log.warn(
                "sendTestMessage: unexpected failure smtpHost={} smtpPort={} cause={}",
                smtpHost,
                smtpPort,
                ex.getMessage(),
                ex
            );
            return EmailTestSmtpResult.failure(
                "SMTP send failed",
                ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                smtpHost,
                smtpPort,
                fromAddress
            );
        }
    }

    private static Integer parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Immutable result of {@link #sendTestMessage(String)}. Surfaced to the
     * controller, which then maps it to {@code EmailTestSmtpResponse}. Kept
     * separate from the wire DTO so the service contract is independent of the
     * controller's response record.
     */
    public record EmailTestSmtpResult(
        boolean ok,
        String message,
        String smtpHost,
        Integer smtpPort,
        String fromAddress,
        String diagnostic
    ) {
        static EmailTestSmtpResult success(String smtpHost, Integer smtpPort, String fromAddress) {
            return new EmailTestSmtpResult(
                true,
                "Test message dispatched",
                smtpHost,
                smtpPort,
                fromAddress,
                null
            );
        }

        static EmailTestSmtpResult failure(
            String message,
            String diagnostic,
            String smtpHost,
            Integer smtpPort,
            String fromAddress
        ) {
            return new EmailTestSmtpResult(
                false,
                message,
                smtpHost,
                smtpPort,
                fromAddress,
                diagnostic
            );
        }
    }
}
