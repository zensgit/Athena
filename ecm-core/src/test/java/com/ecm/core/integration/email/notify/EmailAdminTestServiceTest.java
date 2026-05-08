package com.ecm.core.integration.email.notify;

import com.ecm.core.integration.email.notify.EmailAdminTestService.EmailTestSmtpResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link EmailAdminTestService}.
 *
 * <p>The validation order is part of the contract: recipient → JavaMailSender →
 * from-address → send. An invalid recipient must never touch the mailer.</p>
 *
 * <p>None of these tests assert on the recipient address surfacing in the
 * response body — the response intentionally never carries the destination,
 * to avoid leaking operator-test addresses through diagnostics.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailAdminTestServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    private MockEnvironment environment;

    private EmailAdminTestService newServiceWithEnv(MockEnvironment env) {
        return new EmailAdminTestService(mailSenderProvider, env);
    }

    private static MimeMessage realMimeMessage() {
        // MimeMessageHelper requires an actual MimeMessage with a Session; a
        // pure Mockito stub triggers NPEs inside the helper. A no-network
        // Session is fine — we never call mailSender.send() with a real SMTP.
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    @DisplayName("Happy path: success result returned when JavaMailSender accepts the message")
    void happyPath_dispatchesAndReturnsOk() {
        environment = new MockEnvironment()
            .withProperty("spring.mail.host", "smtp.example.com")
            .withProperty("spring.mail.port", "465")
            .withProperty("ecm.email.from-address", "athena@example.com");
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        EmailTestSmtpResult result = newServiceWithEnv(environment)
            .sendTestMessage("operator@example.com");

        assertThat(result.ok()).isTrue();
        assertThat(result.message()).isEqualTo("Test message dispatched");
        assertThat(result.smtpHost()).isEqualTo("smtp.example.com");
        assertThat(result.smtpPort()).isEqualTo(465);
        assertThat(result.fromAddress()).isEqualTo("athena@example.com");
        assertThat(result.diagnostic()).isNull();

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Missing JavaMailSender: ok=false, diagnostic mentions spring.mail.* config keys")
    void missingMailSender_returnsConfigGuidance() {
        environment = new MockEnvironment()
            .withProperty("ecm.email.from-address", "athena@example.com");
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        EmailTestSmtpResult result = newServiceWithEnv(environment)
            .sendTestMessage("operator@example.com");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("JavaMailSender");
        // Diagnostic must point the operator at the config keys, otherwise
        // the error is opaque and the operator has no actionable next step.
        assertThat(result.diagnostic())
            .contains("spring.mail.host")
            .contains("spring.mail.port");
        // Whatever else changes, we never call into a null sender.
    }

    @Test
    @DisplayName("Blank from-address: ok=false, message references ecm.email.from-address")
    void blankFromAddress_returnsFromAddressMessage() {
        environment = new MockEnvironment()
            .withProperty("spring.mail.host", "smtp.example.com")
            .withProperty("spring.mail.port", "465")
            .withProperty("ecm.email.from-address", "");
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        EmailTestSmtpResult result = newServiceWithEnv(environment)
            .sendTestMessage("operator@example.com");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("from-address");
        // We must not silently default fromAddress — admin must set it
        // explicitly. Verify the mailer was never asked to send.
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Invalid recipient (blank): ok=false with 'Invalid recipient' message")
    void blankRecipient_returnsInvalidRecipient() {
        environment = new MockEnvironment()
            .withProperty("spring.mail.host", "smtp.example.com")
            .withProperty("spring.mail.port", "465")
            .withProperty("ecm.email.from-address", "athena@example.com");

        EmailTestSmtpResult result = newServiceWithEnv(environment).sendTestMessage("");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid recipient");
        // Recipient validation must run BEFORE consulting the mail sender —
        // otherwise a typo'd address would still touch the mailer / log.
        verify(mailSenderProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("Invalid recipient (no '@'): ok=false with 'Invalid recipient' message")
    void recipientWithoutAtSign_returnsInvalidRecipient() {
        environment = new MockEnvironment()
            .withProperty("spring.mail.host", "smtp.example.com")
            .withProperty("spring.mail.port", "465")
            .withProperty("ecm.email.from-address", "athena@example.com");

        EmailTestSmtpResult result = newServiceWithEnv(environment).sendTestMessage("not-an-email");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid recipient");
        verify(mailSenderProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("MailException from sender surfaces ok=false with diagnostic, no token leak")
    void mailExceptionFromSender_surfacesDiagnostic() {
        environment = new MockEnvironment()
            .withProperty("spring.mail.host", "smtp.example.com")
            .withProperty("spring.mail.port", "465")
            .withProperty("ecm.email.from-address", "athena@example.com");
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        // MailSendException is a concrete MailException subclass — the
        // service catches MailException, not Exception, so the catch arm
        // we want exercised is specifically this one.
        MailException sendFailure = new MailSendException("SMTP connect failed: Connection refused");
        doThrow(sendFailure).when(mailSender).send(any(MimeMessage.class));

        EmailTestSmtpResult result = newServiceWithEnv(environment)
            .sendTestMessage("operator@example.com");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo("SMTP send failed");
        assertThat(result.diagnostic()).isEqualTo("SMTP connect failed: Connection refused");
        // Belt-and-braces: the service must never round-trip OAuth tokens
        // back into the diagnostic.
        assertThat(result.diagnostic()).doesNotContain("token");
        assertThat(result.diagnostic()).doesNotContain("password");
    }
}
