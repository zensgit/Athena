# SMTP Preset Extension + Admin Test-SMTP Backend — Design & Verification (2026-05-07)

## Context

This is **Package A** of a 3-package parallel slice. Its job is to:

1. Extend the existing IMAP `MailProviderPreset` enum and its DTO with SMTP
   defaults so the admin form pre-fill carries both inbound and outbound
   metadata in a single request.
2. Add a new admin "Test SMTP" endpoint that lets operators verify
   `spring.mail.*` end-to-end without going through the production
   `EmailNotificationService` async/template path and without flipping
   `ecm.email.enabled`.

The whole slice is gated against `origin/main` at SHA `1fdaffe` and lives on
branch `claude/smtp-backend` until the user integrates it.

### Contract that Package B (frontend) consumes — verbatim

- **Test SMTP endpoint:** `POST /api/v1/admin/email/test-smtp`
  - Body: `{ "to": "operator@example.com" }`
  - Response:
    `{ "ok": boolean, "message": string, "smtpHost": string, "smtpPort": integer, "fromAddress": string, "diagnostic": string|null }`
  - Admin-only (`hasRole('ADMIN')`).
- **Preset list:** `GET /api/v1/integration/mail/provider-presets` now includes
  `smtpHost`, `smtpPort`, `smtpSecurity` per row, **appended after the existing
  IMAP fields**. Final field order on each row:
  `id, label, imapHost, imapPort, imapSecurity, smtpHost, smtpPort, smtpSecurity`.

## Design

### Preset extension

`MailProviderPreset` gains three constructor arguments per constant:
`smtpHost`, `smtpPort`, `smtpSecurity` (a `MailAccount.SecurityType`). The
constructor enforces the same `OAUTH2`-forbidden invariant on `smtpSecurity`
that already applies to `imapSecurity` — these presets describe
username/password mailboxes only; OAuth-based SMTP would carry a token
endpoint and scope, which presets explicitly do not.

`MailProviderPresetResponse` mirrors the enum: existing IMAP fields first,
then SMTP fields appended. Field order is documented as load-bearing in the
record's javadoc and asserted positionally in
`MailAutomationControllerSecurityTest`.

### `EmailAdminController` (new)

Lives at `com.ecm.core.controller.EmailAdminController`, alongside
`OAuthCredentialAdminController` whose pattern it follows:

- `@RequestMapping("/api/v1/admin/email")`
- `@PreAuthorize("hasRole('ADMIN')")` at class level
- `POST /test-smtp` accepts `EmailTestSmtpRequest(String to)`, returns
  `EmailTestSmtpResponse`. Both records are nested inside the controller for
  scoping discipline — they are wire-only types, not domain types.
- The controller is a thin adapter: it delegates to
  `EmailAdminTestService.sendTestMessage(to)` and maps the result to the
  wire DTO. No exception translation is needed; the service never throws.

### `EmailAdminTestService` (new)

Lives at `com.ecm.core.integration.email.notify.EmailAdminTestService`,
beside `EmailNotificationService`. Synchronous (not `@Async`) — the operator
needs the result inline.

Validation order (matches the brief's enumeration; tests assert this order):

1. Recipient must be non-blank and contain `@`. Otherwise return
   `ok=false, message="Invalid recipient"` without consulting the mailer.
2. `JavaMailSender` must be present in the context (via
   `ObjectProvider<JavaMailSender>.getIfAvailable()`). Otherwise return
   `ok=false, message="JavaMailSender not configured",
   diagnostic="Add spring.mail.host / spring.mail.port to application config to enable SMTP."`
3. `ecm.email.from-address` must be non-blank. Otherwise return
   `ok=false, message="ecm.email.from-address not configured"`. **No silent
   default** — admin must set this explicitly.
4. Construct a `MimeMessage` via `MimeMessageHelper`, subject
   `"[Athena] SMTP test"`, body
   `"This is a test message from Athena's admin Test SMTP control. Configuration: spring.mail.host=<host>, spring.mail.port=<port>."`
5. Catch `MailException` → `ok=false, message="SMTP send failed",
   diagnostic=ex.getMessage()`. Catch any other `Exception` → same shape with
   `diagnostic=ex.getClass().getSimpleName() + ": " + ex.getMessage()`.

`smtpHost`, `smtpPort`, `fromAddress` in the response are **always read from
`Environment` regardless of which validation arm fires**, so the operator
sees the currently active configuration even on failure. `spring.mail.port`
that fails `Integer.valueOf` is reported as `null`, never propagates a
`NumberFormatException`.

### Why bypass `ecm.email.enabled`

The whole point of this endpoint is letting operators verify SMTP **before**
turning notifications on. If the test endpoint honoured
`ecm.email.enabled=false`, the operator would have to flip the flag (which
production cron-driven notifications also read) to test connectivity, defeating
the purpose. `EmailNotificationService.send` is unchanged and continues to
short-circuit on the flag for the production path — only this admin
diagnostic bypasses it.

## Verified preset values

| id | smtpHost | smtpPort | smtpSecurity | source URL | WebFetch result |
|---|---|---|---|---|---|
| ALIYUN_QIYE | smtp.qiye.aliyun.com | 465 | SSL | https://help.aliyun.com/zh/document_detail/2937082.html | Confirmed: page lists `smtp.qiye.aliyun.com` with ports 25 (standard) and 465 (encrypted). 994 is **not** referenced on the page revision fetched 2026-05-07; the brief mentions 994 as an alternative but our fetch did not corroborate it. We use 465, matching both the fetched page and Athena's `SecurityType.SSL` semantics. |
| TENCENT_EXMAIL | smtp.exmail.qq.com | 465 | SSL | https://service.rtxmail.net/faq/119.html | **WebFetch failed: certificate has expired** (same fallback as the IMAP round). Values committed are the documented Tencent Exmail defaults referenced by the brief. |
| TENCENT_EXMAIL_OVERSEAS | hwsmtp.exmail.qq.com | 465 | SSL | https://service.rtxmail.net/faq/119.html | **WebFetch failed: certificate has expired.** Values committed are the documented Tencent Exmail overseas defaults referenced by the brief. |
| MAIL_263 | smtp.263.net | 465 | SSL | https://download.263.net/263/helpcenter/client/20160603/970.html | Confirmed: page lists `smtp.263.net` standard port 25 / encrypted (SSL) port 465. |
| MAIL_263_OVERSEAS | smtpw.263.net | 465 | SSL | https://download.263.net/263/helpcenter/client/20160603/970.html | Confirmed: page lists `smtpw.263.net` standard port 25 / encrypted (SSL) port 465. |

### Discrepancies & decisions

- **Aliyun port 994 not visible on the live page.** The brief permits both
  465 and 994 for Aliyun; the fetched page only lists 25 and 465. We commit
  465 because it matches both readings and is the standard SSL/SMTPS port.
- **Tencent docs cert-expired.** The brief explicitly authorises this
  fallback. The committed values match the IMAP round's documented defaults
  for `smtp.exmail.qq.com` / `hwsmtp.exmail.qq.com`.

## Verification

### Targeted test command

```bash
cd /Users/chouhua/Downloads/Github/Athena-smtp-backend/ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=MailProviderPresetTest,MailAutomationControllerSecurityTest,EmailAdminTestServiceTest,EmailAdminControllerSecurityTest test
```

### Result (2026-05-07, branch `claude/smtp-backend`)

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `MailProviderPresetTest` | 6 | 0 | 0 | 0 |
| `MailAutomationControllerSecurityTest` | 13 | 0 | 0 | 0 |
| `EmailAdminTestServiceTest` | 6 | 0 | 0 | 0 |
| `EmailAdminControllerSecurityTest` | 4 | 0 | 0 | 0 |
| **Total** | **29** | **0** | **0** | **0** |

`mvn` reports `BUILD SUCCESS`. `git diff --check` reports no whitespace
issues.

### What each suite covers

- **`MailProviderPresetTest`** — non-null host / positive port / non-null
  security on every constant, both IMAP and SMTP; `smtpSecurity` constrained
  to `SSL`/`STARTTLS`/`NONE` (never `OAUTH2`); DTO mapping pass-through;
  exact host / port / security values per row.
- **`MailAutomationControllerSecurityTest`** — pre-existing 12 tests plus
  the extended `providerPresetsAllowAdminAndExposeNoSecrets` test now also
  asserts `smtpHost`, `smtpPort`, `smtpSecurity` per row, plus the existing
  "no credential leak" guard remains in place.
- **`EmailAdminTestServiceTest`** — happy path success;
  `JavaMailSender` missing → message + diagnostic with `spring.mail.host`/
  `spring.mail.port` guidance; blank `from-address` → ok=false referencing
  `from-address`, mailer never invoked; blank recipient → "Invalid recipient",
  mailer never consulted; recipient missing `@` → same; `MailException` from
  sender → ok=false with verbatim message, no token leak.
- **`EmailAdminControllerSecurityTest`** — anonymous → 401;
  `ROLE_USER` → 403; admin success → 200 with `ok=true` and credential-field
  blacklist; admin failure (service returns ok=false) → 200 with surfaced
  diagnostic. Pattern mirrors `OAuthCredentialAdminControllerSecurityTest`
  exactly.

## Files Changed

### Modified

- `ecm-core/src/main/java/com/ecm/core/integration/mail/preset/MailProviderPreset.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/preset/MailProviderPresetResponse.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/preset/MailProviderPresetTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerSecurityTest.java`

### Added

- `ecm-core/src/main/java/com/ecm/core/controller/EmailAdminController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailAdminTestService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/EmailAdminControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/email/notify/EmailAdminTestServiceTest.java`
- `docs/SMTP_PRESET_AND_TEST_BACKEND_DESIGN_VERIFICATION_20260507.md` (this file)

### Not modified (per brief)

- `ecm-frontend/**` — Package B owns it.
- `EmailNotificationService.java` — async/template production path is
  unchanged; only a sibling sync test path was added.
- `MailAccount.java`, `MailAccountRequest.java`, `MailAccountResponse.java` —
  out of scope.
- No Liquibase migration / DDL changes — preset enum is JVM-only metadata.
- `application.yml` — `ecm.email.enabled` and `ecm.email.from-address`
  already exist (lines 99-101); the new endpoint reads them through
  `Environment` without adding new keys.
- `.env` — not touched anywhere on disk.

## Remaining Work

- **Package B (frontend)** consumes the contract defined above:
  - Render `smtpHost`/`smtpPort`/`smtpSecurity` columns in the preset
    pre-fill UI in addition to the IMAP fields.
  - Add the admin "Test SMTP" form that POSTs to
    `/api/v1/admin/email/test-smtp` and renders `ok` / `message` /
    `diagnostic` / `smtpHost` / `smtpPort` / `fromAddress`.
- **Package C (integration / e2e)** — Playwright spec covering the admin
  Test SMTP flow; load-bearing assertions on the `diagnostic` field
  rendering (null on success, string on failure).
- **End-to-end SMTP integration gate** — `EmailNotificationServiceSmtpTest`
  already exercises `JavaMailSender` against an embedded Greenmail server
  for the production async path; a parallel `EmailAdminTestService` Greenmail
  test is left out of this slice to keep the targeted-test runtime within
  the 60s budget. Add it if the production path's Greenmail coverage
  proves insufficient for this endpoint.
- **Aliyun port 994 confirmation** — if a future operator reports SSL
  handshake failures on 465, we should re-fetch the Aliyun help center for
  994 references. The brief mentioned 994 but our 2026-05-07 fetch did not
  surface it.
