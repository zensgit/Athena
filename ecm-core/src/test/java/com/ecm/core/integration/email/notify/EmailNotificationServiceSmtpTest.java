package com.ecm.core.integration.email.notify;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SMTP integration gate: verifies that EmailNotificationService drives a real
 * SMTP session end-to-end using an embedded Greenmail server. This gate catches
 * MIME encoding or header issues that mocked-sender tests cannot surface.
 *
 * <p>No Spring context — @Async does not activate, send() runs synchronously.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceSmtpTest {

    @Mock private EmailTemplateRepository templateRepository;
    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;

    private GreenMail greenMail;
    private EmailNotificationService service;

    @BeforeEach
    void startSmtp() {
        greenMail = new GreenMail(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());
        greenMail.start();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(greenMail.getSmtp().getPort());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");

        when(mailSenderProvider.getIfAvailable()).thenReturn(sender);

        service = new EmailNotificationService(templateRepository, mailSenderProvider);
        ReflectionTestUtils.setField(service, "emailEnabled", true);
        ReflectionTestUtils.setField(service, "fromAddress", "athena@example.com");
    }

    @AfterEach
    void stopSmtp() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @Test
    @DisplayName("send: delivers rendered plain-text email through real SMTP session")
    void delivers_renderedPlainTextEmail_viaSmtp() throws Exception {
        EmailTemplate template = new EmailTemplate();
        template.setTemplateKey("rm.report_preset.delivery.succeeded");
        template.setLocale("default");
        template.setSubjectTemplate("Athena: ${presetName} delivered");
        template.setBodyTemplate("Preset ${presetName} produced ${filename}. Duration: ${durationMs}ms.");
        template.setHtmlBody(false);

        when(templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(
            eq("rm.report_preset.delivery.succeeded"), any()
        )).thenReturn(List.of(template));

        service.send(
            "rm.report_preset.delivery.succeeded",
            "compliance@example.com",
            "default",
            Map.of(
                "presetName", "Q1 Compliance Audit",
                "filename", "q1-audit-20260426.csv",
                "durationMs", "1234"
            )
        );

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);

        MimeMessage msg = received[0];
        assertThat(msg.getSubject()).isEqualTo("Athena: Q1 Compliance Audit delivered");
        assertThat(msg.getAllRecipients()).hasSize(1);
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("compliance@example.com");
        assertThat(msg.getFrom()[0].toString()).isEqualTo("athena@example.com");

        String body = (String) msg.getContent();
        assertThat(body).contains("Q1 Compliance Audit");
        assertThat(body).contains("q1-audit-20260426.csv");
        assertThat(body).contains("1234ms");
    }

    @Test
    @DisplayName("send: delivers HTML email and preserves content type")
    void delivers_htmlEmail_viaSmtp() throws Exception {
        EmailTemplate template = new EmailTemplate();
        template.setTemplateKey("rm.report_preset.delivery.failed");
        template.setLocale("default");
        template.setSubjectTemplate("Athena alert: ${presetName} delivery failed");
        template.setBodyTemplate("<p>Preset <strong>${presetName}</strong> failed: ${message}</p>");
        template.setHtmlBody(true);

        when(templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(
            eq("rm.report_preset.delivery.failed"), any()
        )).thenReturn(List.of(template));

        service.send(
            "rm.report_preset.delivery.failed",
            "admin@example.com",
            "default",
            Map.of("presetName", "Monthly Report", "message", "Folder not found")
        );

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);

        MimeMessage msg = received[0];
        assertThat(msg.getSubject()).isEqualTo("Athena alert: Monthly Report delivery failed");
        // HTML body is multipart or text/html — content type starts with text/html
        assertThat(msg.getContentType()).startsWith("text/html");
    }

    @Test
    @DisplayName("send: locale fallback delivers template matching the language prefix")
    void delivers_withLocaleFallback_viaSmtp() throws Exception {
        EmailTemplate defTemplate = new EmailTemplate();
        defTemplate.setTemplateKey("rm.report_preset.delivery.succeeded");
        defTemplate.setLocale("default");
        defTemplate.setSubjectTemplate("Default: ${presetName}");
        defTemplate.setBodyTemplate("Default body for ${presetName}");
        defTemplate.setHtmlBody(false);

        EmailTemplate zhTemplate = new EmailTemplate();
        zhTemplate.setTemplateKey("rm.report_preset.delivery.succeeded");
        zhTemplate.setLocale("zh");
        zhTemplate.setSubjectTemplate("成功: ${presetName}");
        zhTemplate.setBodyTemplate("${presetName} 已完成。");
        zhTemplate.setHtmlBody(false);

        // Returns both; service picks zh (requested zh-TW → fallback zh matches)
        when(templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(
            eq("rm.report_preset.delivery.succeeded"), any()
        )).thenReturn(List.of(defTemplate, zhTemplate));

        service.send(
            "rm.report_preset.delivery.succeeded",
            "zh-user@example.com",
            "zh-TW",
            Map.of("presetName", "月報")
        );

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("成功: 月報");
        String body = (String) received[0].getContent();
        assertThat(body).contains("月報 已完成。");
    }
}
