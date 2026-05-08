package com.ecm.core.integration.mail.preset;

import com.ecm.core.integration.mail.model.MailAccount;

/**
 * Static IMAP + SMTP preset metadata for Chinese enterprise mailbox providers.
 *
 * <p>Each constant carries the host / port / security defaults required to
 * pre-fill the admin "create mail account" form for both inbound (IMAP) and
 * outbound (SMTP) configuration. Presets are <strong>metadata only</strong>:
 * no token, password, OAuth client secret or any other credential is ever
 * stored on these constants. The admin still has to supply authentication
 * credentials manually after applying a preset.</p>
 *
 * <p>Both {@link #imapSecurity} and {@link #smtpSecurity} are intentionally
 * constrained to {@link MailAccount.SecurityType#SSL},
 * {@link MailAccount.SecurityType#STARTTLS} or
 * {@link MailAccount.SecurityType#NONE}.
 * {@link MailAccount.SecurityType#OAUTH2} is never used here — these providers
 * are username/password IMAP+SMTP, not OAuth.</p>
 */
public enum MailProviderPreset {

    ALIYUN_QIYE(
        "阿里云企业邮箱",
        "imap.qiye.aliyun.com",
        993,
        MailAccount.SecurityType.SSL,
        "smtp.qiye.aliyun.com",
        465,
        MailAccount.SecurityType.SSL
    ),
    TENCENT_EXMAIL(
        "腾讯企业邮箱",
        "imap.exmail.qq.com",
        993,
        MailAccount.SecurityType.SSL,
        "smtp.exmail.qq.com",
        465,
        MailAccount.SecurityType.SSL
    ),
    TENCENT_EXMAIL_OVERSEAS(
        "腾讯企业邮箱（海外）",
        "hwimap.exmail.qq.com",
        993,
        MailAccount.SecurityType.SSL,
        "hwsmtp.exmail.qq.com",
        465,
        MailAccount.SecurityType.SSL
    ),
    MAIL_263(
        "263 企业邮箱",
        "imap.263.net",
        993,
        MailAccount.SecurityType.SSL,
        "smtp.263.net",
        465,
        MailAccount.SecurityType.SSL
    ),
    MAIL_263_OVERSEAS(
        "263 企业邮箱（海外）",
        "imapw.263.net",
        993,
        MailAccount.SecurityType.SSL,
        "smtpw.263.net",
        465,
        MailAccount.SecurityType.SSL
    );

    private final String label;
    private final String imapHost;
    private final int imapPort;
    private final MailAccount.SecurityType imapSecurity;
    private final String smtpHost;
    private final int smtpPort;
    private final MailAccount.SecurityType smtpSecurity;

    MailProviderPreset(
        String label,
        String imapHost,
        int imapPort,
        MailAccount.SecurityType imapSecurity,
        String smtpHost,
        int smtpPort,
        MailAccount.SecurityType smtpSecurity
    ) {
        if (imapSecurity == MailAccount.SecurityType.OAUTH2) {
            // Guards against accidental future edits — these presets are
            // username/password IMAP only.
            throw new IllegalArgumentException(
                "MailProviderPreset.imapSecurity must not be OAUTH2 (preset=" + name() + ")"
            );
        }
        if (smtpSecurity == MailAccount.SecurityType.OAUTH2) {
            // Same constraint for SMTP — these presets are username/password
            // SMTP only. OAuth-based SMTP would require a different
            // configuration shape (token endpoint, scope, refresh, etc.).
            throw new IllegalArgumentException(
                "MailProviderPreset.smtpSecurity must not be OAUTH2 (preset=" + name() + ")"
            );
        }
        this.label = label;
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.imapSecurity = imapSecurity;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpSecurity = smtpSecurity;
    }

    public String getLabel() {
        return label;
    }

    public String getImapHost() {
        return imapHost;
    }

    public int getImapPort() {
        return imapPort;
    }

    public MailAccount.SecurityType getImapSecurity() {
        return imapSecurity;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public MailAccount.SecurityType getSmtpSecurity() {
        return smtpSecurity;
    }
}
