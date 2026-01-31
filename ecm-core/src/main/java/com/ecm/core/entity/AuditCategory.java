package com.ecm.core.entity;

import java.util.Locale;

public enum AuditCategory {
    NODE,
    VERSION,
    RULE,
    WORKFLOW,
    MAIL,
    INTEGRATION,
    SECURITY,
    PDF,
    OTHER;

    public static AuditCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AuditCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
