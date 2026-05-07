package com.ecm.core.integration.mail.preset;

import com.ecm.core.integration.mail.model.MailAccount;

/**
 * Static IMAP preset metadata for Chinese enterprise mailbox providers.
 *
 * <p>Each constant carries only the host / port / security defaults required to
 * pre-fill the admin "create mail account" form. Presets are <strong>metadata
 * only</strong>: no token, password, OAuth client secret or any other
 * credential is ever stored on these constants. The admin still has to supply
 * authentication credentials manually after applying a preset.</p>
 *
 * <p>The {@link #imapSecurity} value is intentionally constrained to
 * {@link MailAccount.SecurityType#SSL}, {@link MailAccount.SecurityType#STARTTLS}
 * or {@link MailAccount.SecurityType#NONE}. {@link MailAccount.SecurityType#OAUTH2}
 * is never used here — these providers are username/password IMAP, not OAuth.</p>
 */
public enum MailProviderPreset {

    ALIYUN_QIYE(
        "阿里云企业邮箱",
        "imap.qiye.aliyun.com",
        993,
        MailAccount.SecurityType.SSL
    ),
    TENCENT_EXMAIL(
        "腾讯企业邮箱",
        "imap.exmail.qq.com",
        993,
        MailAccount.SecurityType.SSL
    ),
    TENCENT_EXMAIL_OVERSEAS(
        "腾讯企业邮箱（海外）",
        "hwimap.exmail.qq.com",
        993,
        MailAccount.SecurityType.SSL
    ),
    MAIL_263(
        "263 企业邮箱",
        "imap.263.net",
        993,
        MailAccount.SecurityType.SSL
    ),
    MAIL_263_OVERSEAS(
        "263 企业邮箱（海外）",
        "imapw.263.net",
        993,
        MailAccount.SecurityType.SSL
    );

    private final String label;
    private final String imapHost;
    private final int imapPort;
    private final MailAccount.SecurityType imapSecurity;

    MailProviderPreset(
        String label,
        String imapHost,
        int imapPort,
        MailAccount.SecurityType imapSecurity
    ) {
        if (imapSecurity == MailAccount.SecurityType.OAUTH2) {
            // Guards against accidental future edits — these presets are
            // username/password IMAP only.
            throw new IllegalArgumentException(
                "MailProviderPreset.imapSecurity must not be OAUTH2 (preset=" + name() + ")"
            );
        }
        this.label = label;
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.imapSecurity = imapSecurity;
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
}
