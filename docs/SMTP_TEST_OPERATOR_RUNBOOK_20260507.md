# SMTP Test - Operator Runbook

Date: 2026-05-07

## Context

Athena ships an admin-only "Test SMTP" control on the Mail Automation admin page so that operators can validate the application-level outbound mail configuration (`spring.mail.*` plus `ecm.email.from-address`) without dispatching a real notification template. The control submits a single recipient address; the backend opens a `JavaMailSender` connection using the live `spring.mail.*` settings, sends a fixed `[Athena] SMTP test` subject, and returns a structured success or failure response. This runbook walks an operator through enabling SMTP for the application, running the Test SMTP control, and interpreting the result.

The Test SMTP control is the fastest path to confirm "is the JVM able to authenticate against this SMTP server right now?" before triggering a real notification flow (such as a site invitation). It is intentionally separate from the per-account IMAP `Test connection` control on each `MailAccount` row, which validates inbound IMAP fetching against per-account credentials and does not touch `spring.mail.*`.

## Required configuration

The Test SMTP control reads the same Spring configuration that any production notification dispatch reads. Set the following env vars (or their `application.yml` equivalents) and restart the application before testing:

| Property | Env var | Required | Notes |
|---|---|---|---|
| `spring.mail.host` | `SPRING_MAIL_HOST` | yes | e.g. `smtp.qiye.aliyun.com` |
| `spring.mail.port` | `SPRING_MAIL_PORT` | yes | e.g. `465` |
| `spring.mail.username` | `SPRING_MAIL_USERNAME` | yes | full email address |
| `spring.mail.password` | `SPRING_MAIL_PASSWORD` | yes | client authorization code where the provider requires one (Tencent, 263). NEVER commit this. |
| `spring.mail.properties.mail.smtp.auth` | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | yes | `true` |
| `spring.mail.properties.mail.smtp.ssl.enable` | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE` | for SSL/465 | `true` |
| `spring.mail.properties.mail.smtp.starttls.enable` | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | for STARTTLS/587 | `true` |
| `ecm.email.from-address` | `ECM_EMAIL_FROM_ADDRESS` | yes | sender display address; usually equal to `spring.mail.username` |
| `ecm.email.enabled` | `ECM_EMAIL_ENABLED` | no for the test endpoint, yes for live notifications | the Test SMTP control bypasses this flag intentionally; site invitation emails do NOT |

The provider-side prerequisites (enabling SMTP at the admin console, generating a client authorization code, and the canonical host/port/security for each Chinese enterprise mailbox preset) are documented in [`MAIL_PROVIDER_SETUP_ALIYUN_TENCENT_263_20260507.md`](./MAIL_PROVIDER_SETUP_ALIYUN_TENCENT_263_20260507.md). The operator should pull the SMTP host / port / security values straight from the preset table in that doc (or from the read-only SMTP defaults block under the preset dropdown in the UI).

## Test sequence

1. Set the env vars listed in "Required configuration" above and restart the application. Do NOT skip the restart — `spring.mail.*` is bound at context startup.
2. Sign in as an admin (`ROLE_ADMIN`) and open the Mail Automation page.
3. Click `Test SMTP`. The dialog opens.
4. Enter your own (operator-controlled) email address in the recipient field. Do NOT use a colleague's address for the first test — you want to be able to confirm receipt yourself.
5. Click `Send test`.
6. Expected on success: a green Alert reads `Sent! SMTP <host>:<port> from <fromAddress>`. Check the inbox of the recipient address; the message subject is `[Athena] SMTP test`. The subject is fixed by the backend and does NOT consult any database template.
7. Expected on failure: a red Alert with a short backend message (e.g. `JavaMailSender not configured`, `ecm.email.from-address not configured`, `SMTP send failed`) and a diagnostic block beneath. The `diagnostic` field is the actual underlying exception message (e.g. `MailAuthenticationException: Authentication failed`); use it to narrow the cause via the FAQ below.

If the dialog shows a network or auth error before reaching the green/red Alert (e.g. `403 Forbidden`), the operator account is not `ROLE_ADMIN` or the admin endpoint is unavailable — that is a permission/wiring issue, not an SMTP issue, and the FAQ below does not apply.

## Common failure modes

- **`JavaMailSender not configured`.** `spring.mail.host` is unset or empty. Spring did not auto-configure a `JavaMailSender` bean at startup. Fix: set `SPRING_MAIL_HOST` (and the other required `SPRING_MAIL_*` vars) and restart.
- **`ecm.email.from-address not configured`.** The application has a `JavaMailSender` but no sender address. Fix: set `ECM_EMAIL_FROM_ADDRESS` to the same value as `SPRING_MAIL_USERNAME` (or to a valid display address that the provider accepts as a `From:` header) and restart.
- **`MailAuthenticationException: 535 ...` / `Authentication failed`.** Wrong password, OR the provider requires a separate "client authorization code" instead of the regular login password. Aliyun calls it 三方客户端安全密码; Tencent calls it 客户端专用密码; 263 calls it 客户端授权码. Generate the code at the provider's webmail (per the per-provider section of `MAIL_PROVIDER_SETUP_ALIYUN_TENCENT_263_20260507.md`) and re-set `SPRING_MAIL_PASSWORD` to that value, then restart.
- **`SSLHandshakeException` / `unable to find valid certification path`.** Port and security mode do not match. 465 is implicit TLS — set `mail.smtp.ssl.enable=true` and leave `mail.smtp.starttls.enable` unset (or `false`). 587 is STARTTLS — set `mail.smtp.starttls.enable=true` and leave `mail.smtp.ssl.enable` unset. Port 25 is unencrypted and rarely supported by enterprise providers; do not use it for production. If the provider has rotated certificates, the JVM truststore may be stale — refresh the truststore in that case.
- **`MailSendException: Could not connect to SMTP host`.** Network reachability problem. Confirm the host is correct against the per-provider table in `MAIL_PROVIDER_SETUP_ALIYUN_TENCENT_263_20260507.md`. Confirm outbound firewall rules allow the chosen port (usually 465 or 587) from the application host. For Chinese mailbox providers hosted outside mainland China, switch to the `*_OVERSEAS` preset (`hwsmtp.exmail.qq.com` / `smtpw.263.net`) when the domestic host is slow or unreachable.

## What the test endpoint does NOT validate

The Test SMTP control is deliberately narrow. The following are explicitly out of its scope:

- It does NOT validate `ecm.email.enabled`. The control bypasses that flag so an operator can wire `spring.mail.*` against a fresh deployment without first enabling live notifications across the application. Real notification dispatch (e.g. a site invitation) checks `ecm.email.enabled` separately and silently skips when it is `false`.
- It does NOT validate any specific notification template. Templates are tested by triggering the actual workflow they feed (e.g. creating a site invitation triggers the `site.invitation` template — see [`SITE_INVITATION_LIVE_SEND_CLOSEOUT_20260507.md`](./SITE_INVITATION_LIVE_SEND_CLOSEOUT_20260507.md)).
- It does NOT exercise any per-account IMAP setting. The `MailAccount` rows on the Mail Automation admin page each have their own `Test connection` control at `POST /api/v1/integration/mail/accounts/{id}/test`; that endpoint validates inbound IMAP fetch against the per-account credentials and is independent of `spring.mail.*`.
- It does NOT validate locale fallback, HTML body rendering, or placeholder substitution. The fixed `[Athena] SMTP test` payload has no placeholders and no HTML body.

## Out of scope

- **OAuth-based SMTP.** None of the Chinese enterprise mailbox presets ship with OAuth in v1; the Test SMTP control assumes username/password (or username/authorization-code) auth and does not exercise an OAuth token flow.
- **Gmail / Outlook / Microsoft 365 SMTP.** Not covered by this runbook. Those providers have their own OAuth/app-password rules that fall outside the Chinese-enterprise-mailbox preset slice this runbook accompanies.
- **Per-recipient delivery tracking.** The Test SMTP control reports SMTP-level send success (the message left the application and the SMTP server accepted it). It does NOT confirm the recipient's mail server accepted final delivery, and it does NOT track bounces. To confirm receipt, the operator looks in the recipient's inbox.
