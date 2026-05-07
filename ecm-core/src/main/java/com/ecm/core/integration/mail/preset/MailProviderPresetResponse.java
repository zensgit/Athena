package com.ecm.core.integration.mail.preset;

import com.ecm.core.integration.mail.model.MailAccount;

/**
 * Wire-format DTO for {@link MailProviderPreset}.
 *
 * <p>Serialised as JSON for {@code GET /api/v1/integration/mail/provider-presets}.
 * Contains <strong>only</strong> host / port / security metadata; never a
 * password, OAuth token, or client secret.</p>
 *
 * @param id            matches the {@link MailProviderPreset} enum constant name
 *                      (e.g. {@code ALIYUN_QIYE}).
 * @param label         user-facing Chinese display label for the admin form.
 * @param imapHost      IMAP server hostname (no protocol prefix).
 * @param imapPort      IMAP server port; positive integer.
 * @param imapSecurity  one of {@link MailAccount.SecurityType#SSL},
 *                      {@link MailAccount.SecurityType#STARTTLS},
 *                      {@link MailAccount.SecurityType#NONE}; never
 *                      {@link MailAccount.SecurityType#OAUTH2}.
 */
public record MailProviderPresetResponse(
    String id,
    String label,
    String imapHost,
    int imapPort,
    MailAccount.SecurityType imapSecurity
) {

    /**
     * Map a {@link MailProviderPreset} enum value to its wire representation.
     */
    public static MailProviderPresetResponse from(MailProviderPreset preset) {
        return new MailProviderPresetResponse(
            preset.name(),
            preset.getLabel(),
            preset.getImapHost(),
            preset.getImapPort(),
            preset.getImapSecurity()
        );
    }
}
