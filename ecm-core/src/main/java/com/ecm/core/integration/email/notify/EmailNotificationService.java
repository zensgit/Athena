package com.ecm.core.integration.email.notify;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.PropertyPlaceholderHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    static final String DEFAULT_LOCALE = "default";

    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER =
        new PropertyPlaceholderHelper("${", "}", ":", true);

    private final EmailTemplateRepository templateRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${ecm.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${ecm.email.from-address:}")
    private String fromAddress;

    /**
     * Outcome of a synchronous send. {@code ok=true} means the underlying
     * JavaMailSender accepted the message. {@code error} is null on success
     * and carries a stable, human-readable reason on failure (caller may
     * persist or display it).
     */
    public record SendResult(boolean ok, String error) {}

    /**
     * Synchronous send. Returns deterministic {@link SendResult} so callers
     * (e.g. SiteInvitationService) can record per-recipient outcomes. The
     * legacy {@link #send(String, String, String, Map)} keeps fire-and-forget
     * semantics and simply ignores the result.
     */
    public SendResult sendSync(
        String templateKey,
        String toAddress,
        String preferredLocale,
        Map<String, Object> variables
    ) {
        if (!emailEnabled) {
            log.debug("sendSync: ecm.email.enabled=false; skipping templateKey={}", templateKey);
            return new SendResult(false, "ecm.email.enabled is false");
        }
        if (toAddress == null || toAddress.isBlank()) {
            log.warn("sendSync: missing recipient for templateKey={}", templateKey);
            return new SendResult(false, "recipient address is blank");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("sendSync: ecm.email.from-address not configured; skipping templateKey={}", templateKey);
            return new SendResult(false, "ecm.email.from-address is not configured");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("sendSync: JavaMailSender not configured; skipping templateKey={}", templateKey);
            return new SendResult(false, "JavaMailSender is not configured");
        }

        EmailTemplate template = resolveTemplate(templateKey, preferredLocale);
        if (template == null) {
            log.warn(
                "sendSync: template not found key={} locale={}",
                templateKey,
                preferredLocale
            );
            return new SendResult(false, "template not found: " + templateKey + " for locale fallback");
        }

        Properties props = toProperties(variables);

        try {
            String subject = PLACEHOLDER_HELPER.replacePlaceholders(template.getSubjectTemplate(), props);
            String body = PLACEHOLDER_HELPER.replacePlaceholders(template.getBodyTemplate(), props);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(body, template.isHtmlBody());

            mailSender.send(message);

            log.info(
                "sendSync: dispatched templateKey={} subjectLen={} bodyLen={}",
                templateKey,
                subject.length(),
                body.length()
            );
            return new SendResult(true, null);
        } catch (MailException ex) {
            log.warn(
                "sendSync: mail dispatch failed templateKey={} cause={}",
                templateKey,
                ex.getMessage()
            );
            return new SendResult(false, "SMTP send failed: " + ex.getMessage());
        } catch (Exception ex) {
            log.warn(
                "sendSync: unexpected failure templateKey={} cause={}",
                templateKey,
                ex.getMessage(),
                ex
            );
            return new SendResult(
                false,
                "unexpected send failure: " + ex.getClass().getSimpleName() + " " + ex.getMessage()
            );
        }
    }

    /**
     * Legacy fire-and-forget send. Kept signature-compatible for existing
     * callers (e.g. RM report-preset notifications) which never inspected
     * the outcome. New callers that need success/failure tracking should
     * call {@link #sendSync(String, String, String, Map)} directly.
     */
    @Async
    public void send(
        String templateKey,
        String toAddress,
        String preferredLocale,
        Map<String, Object> variables
    ) {
        sendSync(templateKey, toAddress, preferredLocale, variables);
    }

    EmailTemplate resolveTemplate(String templateKey, String preferredLocale) {
        if (templateKey == null || templateKey.isBlank()) {
            return null;
        }
        List<String> fallbacks = computeLocaleFallbacks(preferredLocale);
        List<EmailTemplate> candidates =
            templateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(templateKey, fallbacks);
        if (candidates.isEmpty()) {
            return null;
        }
        for (String locale : fallbacks) {
            for (EmailTemplate candidate : candidates) {
                if (locale.equalsIgnoreCase(candidate.getLocale())) {
                    return candidate;
                }
            }
        }
        return candidates.get(0);
    }

    static List<String> computeLocaleFallbacks(String preferredLocale) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (preferredLocale != null && !preferredLocale.isBlank()) {
            String trimmed = preferredLocale.trim();
            ordered.add(trimmed);
            int dash = trimmed.indexOf('-');
            int underscore = trimmed.indexOf('_');
            int sep = (dash > 0 && (underscore < 0 || dash < underscore)) ? dash : underscore;
            if (sep > 0) {
                ordered.add(trimmed.substring(0, sep).toLowerCase(Locale.ROOT));
            }
        }
        ordered.add(DEFAULT_LOCALE);
        return new ArrayList<>(ordered);
    }

    private static Properties toProperties(Map<String, Object> variables) {
        Properties props = new Properties();
        if (variables == null) {
            return props;
        }
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            Object value = entry.getValue();
            props.setProperty(entry.getKey(), value == null ? "" : value.toString());
        }
        return props;
    }

}
