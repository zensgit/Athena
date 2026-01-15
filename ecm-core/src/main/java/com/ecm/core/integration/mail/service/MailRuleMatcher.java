package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.model.MailRule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class MailRuleMatcher {

    private MailRuleMatcher() {
    }

    record MailMessageData(
        String subject,
        String from,
        String to,
        String body,
        List<String> attachmentNames,
        LocalDateTime receivedAt
    ) {}

    static boolean matches(MailRule rule, MailMessageData data) {
        if (!matchesRegex(rule.getSubjectFilter(), data.subject())) {
            return false;
        }
        if (!matchesRegex(rule.getFromFilter(), data.from())) {
            return false;
        }
        if (!matchesRegex(rule.getToFilter(), data.to())) {
            return false;
        }
        if (!matchesRegex(rule.getBodyFilter(), data.body())) {
            return false;
        }

        if (!matchesAttachmentFilters(rule, data.attachmentNames())) {
            return false;
        }

        if (!matchesMaxAge(rule.getMaxAgeDays(), data.receivedAt())) {
            return false;
        }

        return true;
    }

    private static boolean matchesRegex(String pattern, String value) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        String target = value != null ? value : "";
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(target).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    static boolean matchesAttachmentFilters(MailRule rule, List<String> attachmentNames) {
        List<String> names = attachmentNames != null ? attachmentNames : List.of();

        String include = rule.getAttachmentFilenameInclude();
        if (include != null && !include.isBlank()) {
            if (names.isEmpty()) {
                return false;
            }
            boolean anyMatch = names.stream().anyMatch(name -> wildcardMatches(include, name));
            if (!anyMatch) {
                return false;
            }
        }

        String exclude = rule.getAttachmentFilenameExclude();
        if (exclude != null && !exclude.isBlank()) {
            boolean anyMatch = names.stream().anyMatch(name -> wildcardMatches(exclude, name));
            if (anyMatch) {
                return false;
            }
        }

        return true;
    }

    private static boolean wildcardMatches(String pattern, String value) {
        if (value == null) {
            return false;
        }
        String regex = wildcardToRegex(pattern);
        return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE).matcher(value).find();
    }

    private static String wildcardToRegex(String pattern) {
        StringBuilder builder = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            switch (ch) {
                case '*' -> builder.append(".*");
                case '?' -> builder.append(".");
                case '\\', '.', '[', ']', '{', '}', '(', ')', '+', '-', '^', '$', '|' -> builder.append("\\").append(ch);
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static boolean matchesMaxAge(Integer maxAgeDays, LocalDateTime receivedAt) {
        if (maxAgeDays == null || maxAgeDays <= 0 || receivedAt == null) {
            return true;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(maxAgeDays);
        return !receivedAt.isBefore(cutoff);
    }
}
