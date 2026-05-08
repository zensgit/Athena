package com.ecm.core.integration.email.notify;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock private EmailTemplateRepository templateRepository;
    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock private JavaMailSender mailSender;

    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailNotificationService(templateRepository, mailSenderProvider);
        ReflectionTestUtils.setField(service, "emailEnabled", true);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@athena.local");
    }

    private void mailSenderAvailable() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
    }

    @Test
    @DisplayName("send: skips silently when ecm.email.enabled=false")
    void skipsSilently_whenDisabled() {
        ReflectionTestUtils.setField(service, "emailEnabled", false);

        service.send("rm.report_preset.delivery.succeeded", "user@example.com", "default", Map.of());

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("send: skips silently when toAddress is blank")
    void skipsSilently_whenToBlank() {
        service.send("any.key", "   ", "default", Map.of());

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("send: skips silently when fromAddress is blank")
    void skipsSilently_whenFromBlank() {
        ReflectionTestUtils.setField(service, "fromAddress", "");

        service.send("any.key", "user@example.com", "default", Map.of());

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("send: skips silently when JavaMailSender is unavailable")
    void skipsSilently_whenMailSenderUnavailable() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        service.send("any.key", "user@example.com", "default", Map.of());

        verify(templateRepository, never()).findByTemplateKeyAndLocaleInOrderByLocaleAsc(any(), any());
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("send: warns and returns when template not found")
    void returnsEarly_whenTemplateNotFound() {
        mailSenderAvailable();
        when(templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(
            eq("missing.key"), any())).thenReturn(List.of());

        service.send("missing.key", "user@example.com", "default", Map.of());

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("send: dispatches MimeMessage with rendered subject and body when enabled")
    void sendsRenderedMessage_whenEnabled() throws Exception {
        mailSenderAvailable();
        EmailTemplate template = new EmailTemplate();
        template.setTemplateKey("rm.report_preset.delivery.succeeded");
        template.setLocale("default");
        template.setSubjectTemplate("Athena: ${presetName} delivered");
        template.setBodyTemplate("Preset ${presetName} produced ${filename}.");
        template.setHtmlBody(false);

        when(templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(
            eq("rm.report_preset.delivery.succeeded"), any())).thenReturn(List.of(template));

        MimeMessage realMime = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(realMime);

        Map<String, Object> vars = new HashMap<>();
        vars.put("presetName", "Q1 Audit Report");
        vars.put("filename", "audit-2026-04-26.xlsx");

        service.send(
            "rm.report_preset.delivery.succeeded",
            "compliance@example.com",
            "default",
            vars
        );

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Athena: Q1 Audit Report delivered");
        assertThat(sent.getAllRecipients()).hasSize(1);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("compliance@example.com");
        assertThat(sent.getFrom()[0].toString()).isEqualTo("no-reply@athena.local");
    }

    @Test
    @DisplayName("send: swallows MailException without bubbling to caller")
    void logsAndSwallows_whenSendThrows() {
        mailSenderAvailable();
        EmailTemplate template = new EmailTemplate();
        template.setTemplateKey("k");
        template.setLocale("default");
        template.setSubjectTemplate("S");
        template.setBodyTemplate("B");
        template.setHtmlBody(false);

        when(templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(
            eq("k"), any())).thenReturn(List.of(template));

        MimeMessage realMime = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(realMime);

        org.mockito.Mockito.doThrow(new MailSendException("smtp down"))
            .when(mailSender).send(any(MimeMessage.class));

        // Must not throw
        service.send("k", "u@example.com", "default", Map.of());

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendSync: returns SendResult(true, null) on successful dispatch")
    void sendSyncReturnsOkOnSuccess() {
        mailSenderAvailable();
        EmailTemplate template = new EmailTemplate();
        template.setTemplateKey("k");
        template.setLocale("default");
        template.setSubjectTemplate("S");
        template.setBodyTemplate("B");
        template.setHtmlBody(false);
        when(templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(eq("k"), any()))
            .thenReturn(List.of(template));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        EmailNotificationService.SendResult result =
            service.sendSync("k", "u@example.com", "default", Map.of());

        assertThat(result.ok()).isTrue();
        assertThat(result.error()).isNull();
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendSync: returns SendResult(false, 'SMTP send failed: ...') when MailException is thrown")
    void sendSyncReturnsFailureWhenMailExceptionThrown() {
        mailSenderAvailable();
        EmailTemplate template = new EmailTemplate();
        template.setTemplateKey("k");
        template.setLocale("default");
        template.setSubjectTemplate("S");
        template.setBodyTemplate("B");
        template.setHtmlBody(false);
        when(templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(eq("k"), any()))
            .thenReturn(List.of(template));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        org.mockito.Mockito.doThrow(new MailSendException("smtp down"))
            .when(mailSender).send(any(MimeMessage.class));

        EmailNotificationService.SendResult result =
            service.sendSync("k", "u@example.com", "default", Map.of());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).startsWith("SMTP send failed:");
        assertThat(result.error()).contains("smtp down");
    }

    @Test
    @DisplayName("resolveTemplate: prefers exact locale, then language, then default")
    void resolvesTemplateByLocale_withFallbacks() {
        EmailTemplate zhCN = new EmailTemplate();
        zhCN.setTemplateKey("k");
        zhCN.setLocale("zh-CN");

        EmailTemplate zh = new EmailTemplate();
        zh.setTemplateKey("k");
        zh.setLocale("zh");

        EmailTemplate def = new EmailTemplate();
        def.setTemplateKey("k");
        def.setLocale("default");

        when(templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(
            eq("k"), any())).thenReturn(List.of(def, zh, zhCN));

        EmailTemplate resolved = service.resolveTemplate("k", "zh-CN");
        assertThat(resolved).isSameAs(zhCN);

        EmailTemplate resolvedLang = service.resolveTemplate("k", "zh-TW");
        assertThat(resolvedLang).isSameAs(zh);

        EmailTemplate resolvedDefault = service.resolveTemplate("k", "fr");
        assertThat(resolvedDefault).isSameAs(def);
    }

    @Test
    @DisplayName("computeLocaleFallbacks: produces exact → language → default chain")
    void computeLocaleFallbacks_chain() {
        assertThat(EmailNotificationService.computeLocaleFallbacks("zh-CN"))
            .containsExactly("zh-CN", "zh", "default");
        assertThat(EmailNotificationService.computeLocaleFallbacks("en"))
            .containsExactly("en", "default");
        assertThat(EmailNotificationService.computeLocaleFallbacks(null))
            .containsExactly("default");
        assertThat(EmailNotificationService.computeLocaleFallbacks(""))
            .containsExactly("default");
        assertThat(EmailNotificationService.computeLocaleFallbacks("fr_CA"))
            .containsExactly("fr_CA", "fr", "default");
    }
}
