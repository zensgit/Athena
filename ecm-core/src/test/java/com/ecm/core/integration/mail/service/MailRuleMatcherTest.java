package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.model.MailRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailRuleMatcherTest {

    @Test
    void matchesSubjectAndRecipientFilters() {
        MailRule rule = new MailRule();
        rule.setSubjectFilter("invoice");
        rule.setToFilter("finance@");

        MailRuleMatcher.MailMessageData data = new MailRuleMatcher.MailMessageData(
            "Monthly Invoice",
            "sender@example.com",
            "finance@example.com",
            "Body text",
            List.of(),
            LocalDateTime.now()
        );

        assertTrue(MailRuleMatcher.matches(rule, data));

        rule.setToFilter("legal@");
        assertFalse(MailRuleMatcher.matches(rule, data));
    }

    @Test
    void respectsAttachmentIncludeExcludeFilters() {
        MailRule rule = new MailRule();
        rule.setAttachmentFilenameInclude("*.pdf");
        rule.setAttachmentFilenameExclude("*secret*");

        MailRuleMatcher.MailMessageData match = new MailRuleMatcher.MailMessageData(
            "Invoice",
            "sender@example.com",
            "finance@example.com",
            "Body",
            List.of("invoice.pdf"),
            LocalDateTime.now()
        );

        assertTrue(MailRuleMatcher.matches(rule, match));

        MailRuleMatcher.MailMessageData excluded = new MailRuleMatcher.MailMessageData(
            "Invoice",
            "sender@example.com",
            "finance@example.com",
            "Body",
            List.of("secret.pdf"),
            LocalDateTime.now()
        );

        assertFalse(MailRuleMatcher.matches(rule, excluded));
    }

    @Test
    void skipsMessagesOlderThanMaxAge() {
        MailRule rule = new MailRule();
        rule.setMaxAgeDays(7);

        MailRuleMatcher.MailMessageData stale = new MailRuleMatcher.MailMessageData(
            "Invoice",
            "sender@example.com",
            "finance@example.com",
            "Body",
            List.of(),
            LocalDateTime.now().minusDays(10)
        );

        assertFalse(MailRuleMatcher.matches(rule, stale));
    }
}
