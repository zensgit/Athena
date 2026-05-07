package com.ecm.core.integration.mail.preset;

import com.ecm.core.integration.mail.model.MailAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link MailProviderPreset} and
 * {@link MailProviderPresetResponse}.
 *
 * <p>These tests guard the contract Package B (frontend) consumes: the enum
 * names are wire identifiers, the security values are restricted to a
 * non-OAuth subset, and the DTO mapping is straight pass-through.</p>
 */
class MailProviderPresetTest {

    private static final Set<MailAccount.SecurityType> ALLOWED_SECURITY = EnumSet.of(
        MailAccount.SecurityType.SSL,
        MailAccount.SecurityType.STARTTLS,
        MailAccount.SecurityType.NONE
    );

    @Test
    @DisplayName("All five required preset constants are present")
    void allFivePresetsArePresent() {
        // Names are part of the wire contract — Package B consumes them.
        assertThat(MailProviderPreset.values()).extracting(Enum::name)
            .containsExactly(
                "ALIYUN_QIYE",
                "TENCENT_EXMAIL",
                "TENCENT_EXMAIL_OVERSEAS",
                "MAIL_263",
                "MAIL_263_OVERSEAS"
            );
    }

    @Test
    @DisplayName("Each preset has non-null host, positive port, non-null security")
    void everyPresetHasValidFields() {
        for (MailProviderPreset preset : MailProviderPreset.values()) {
            assertThat(preset.getLabel())
                .as("label for %s", preset.name())
                .isNotNull()
                .isNotBlank();
            assertThat(preset.getImapHost())
                .as("imapHost for %s", preset.name())
                .isNotNull()
                .isNotBlank();
            assertThat(preset.getImapPort())
                .as("imapPort for %s", preset.name())
                .isPositive();
            assertThat(preset.getImapSecurity())
                .as("imapSecurity for %s", preset.name())
                .isNotNull();
        }
    }

    @Test
    @DisplayName("imapSecurity is always SSL/STARTTLS/NONE — never OAUTH2")
    void imapSecurityIsNeverOAuth2() {
        for (MailProviderPreset preset : MailProviderPreset.values()) {
            assertThat(preset.getImapSecurity())
                .as("imapSecurity for %s must be SSL/STARTTLS/NONE", preset.name())
                .isIn(ALLOWED_SECURITY)
                .isNotEqualTo(MailAccount.SecurityType.OAUTH2);
        }
    }

    @Test
    @DisplayName("from(...) maps enum to DTO with id matching the enum name")
    void fromMapsEnumNameToDtoId() {
        for (MailProviderPreset preset : MailProviderPreset.values()) {
            MailProviderPresetResponse dto = MailProviderPresetResponse.from(preset);

            assertThat(dto.id()).isEqualTo(preset.name());
            assertThat(dto.label()).isEqualTo(preset.getLabel());
            assertThat(dto.imapHost()).isEqualTo(preset.getImapHost());
            assertThat(dto.imapPort()).isEqualTo(preset.getImapPort());
            assertThat(dto.imapSecurity()).isEqualTo(preset.getImapSecurity());
        }
    }

    @Test
    @DisplayName("Specific preset values match the Chinese enterprise mailbox sources")
    void verifiedPresetValues() {
        // These values are the contract Package B will pre-fill in the
        // create-account form. Changing them silently would mis-configure
        // every newly created account for the affected provider.
        assertThat(MailProviderPreset.ALIYUN_QIYE.getImapHost()).isEqualTo("imap.qiye.aliyun.com");
        assertThat(MailProviderPreset.ALIYUN_QIYE.getImapPort()).isEqualTo(993);
        assertThat(MailProviderPreset.ALIYUN_QIYE.getImapSecurity()).isEqualTo(MailAccount.SecurityType.SSL);

        assertThat(MailProviderPreset.TENCENT_EXMAIL.getImapHost()).isEqualTo("imap.exmail.qq.com");
        assertThat(MailProviderPreset.TENCENT_EXMAIL.getImapPort()).isEqualTo(993);
        assertThat(MailProviderPreset.TENCENT_EXMAIL.getImapSecurity()).isEqualTo(MailAccount.SecurityType.SSL);

        assertThat(MailProviderPreset.TENCENT_EXMAIL_OVERSEAS.getImapHost()).isEqualTo("hwimap.exmail.qq.com");
        assertThat(MailProviderPreset.TENCENT_EXMAIL_OVERSEAS.getImapPort()).isEqualTo(993);
        assertThat(MailProviderPreset.TENCENT_EXMAIL_OVERSEAS.getImapSecurity()).isEqualTo(MailAccount.SecurityType.SSL);

        assertThat(MailProviderPreset.MAIL_263.getImapHost()).isEqualTo("imap.263.net");
        assertThat(MailProviderPreset.MAIL_263.getImapPort()).isEqualTo(993);
        assertThat(MailProviderPreset.MAIL_263.getImapSecurity()).isEqualTo(MailAccount.SecurityType.SSL);

        assertThat(MailProviderPreset.MAIL_263_OVERSEAS.getImapHost()).isEqualTo("imapw.263.net");
        assertThat(MailProviderPreset.MAIL_263_OVERSEAS.getImapPort()).isEqualTo(993);
        assertThat(MailProviderPreset.MAIL_263_OVERSEAS.getImapSecurity()).isEqualTo(MailAccount.SecurityType.SSL);
    }
}
