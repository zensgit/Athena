# Mail Provider Presets — Backend Design & Verification (2026-05-07)

Package A of the 3-package "Chinese enterprise mailbox presets" slice. The
backend exposes static IMAP host / port / security defaults so the admin UI
(Package B) can pre-fill the `Create Mail Account` form for Aliyun, Tencent
exmail, and 263. No `MailAccount` schema change. No credential disclosure.

---

## 1. Context

Athena admins manually configure IMAP server hostname, port, and SSL/STARTTLS
mode for every new mail account. For three of the most common Chinese
enterprise mailbox providers (Aliyun, Tencent exmail, 263) those values are
public, well-known constants. Forcing admins to look them up — and risking
typos — is friction we can remove with a static metadata endpoint.

Out of scope for this slice (deliberately):

- SMTP presets (Package C will document those).
- OAuth / app-password presets — these providers are username/password IMAP.
- Microsoft 365 / Gmail / generic IMAP — not in the spec.
- Any change to `MailAccount`, `MailAccountRequest`, `MailAccountResponse`.
- Any DB migration / DDL change. There is no persisted preset table.

---

## 2. Design

### 2.1 Enum: `MailProviderPreset`

`ecm-core/src/main/java/com/ecm/core/integration/mail/preset/MailProviderPreset.java`

A 5-constant Java enum, each constant carrying:

- `label` — Chinese display label for the admin form.
- `imapHost` — IMAP server hostname (no protocol prefix).
- `imapPort` — IMAP server port (positive `int`).
- `imapSecurity` — `MailAccount.SecurityType`, restricted to `SSL`,
  `STARTTLS`, or `NONE` at the constructor (any future edit that sets
  `OAUTH2` fails fast with `IllegalArgumentException` at class init).

The constants are declared in fixed order (`ALIYUN_QIYE`, `TENCENT_EXMAIL`,
`TENCENT_EXMAIL_OVERSEAS`, `MAIL_263`, `MAIL_263_OVERSEAS`) so
`Arrays.stream(values())` yields a deterministic JSON array — Package B
relies on this ordering.

Java identifiers cannot start with a digit, so the user-supplied names
"263 / 263_OVERSEAS" map to enum constants `MAIL_263` and
`MAIL_263_OVERSEAS`. The `label` field still surfaces the user-facing
"263 企业邮箱" / "263 企业邮箱（海外）" wording.

### 2.2 DTO: `MailProviderPresetResponse`

`ecm-core/src/main/java/com/ecm/core/integration/mail/preset/MailProviderPresetResponse.java`

A Java `record` with the wire shape:

```json
{
  "id": "ALIYUN_QIYE",
  "label": "阿里云企业邮箱",
  "imapHost": "imap.qiye.aliyun.com",
  "imapPort": 993,
  "imapSecurity": "SSL"
}
```

`id` matches `MailProviderPreset.name()` exactly (the name is the wire
identifier Package B keys on). The static factory
`MailProviderPresetResponse.from(MailProviderPreset)` is straight pass-through.

The DTO has **no** `password`, `oauthAccessToken`, `oauthRefreshToken`,
`oauthClientSecret`, or `oauthClientId` field. There is no code path that
attaches a credential to a preset.

### 2.3 Endpoint

Mounted on the existing `MailAutomationController` (which uses
`@RequestMapping("/api/v1/integration/mail")`):

```
GET /api/v1/integration/mail/provider-presets
```

> **Path note for Package B**: the original parallel-slice spec listed the
> path as `/api/v1/mail/automation/provider-presets`, but the existing
> `MailAutomationController` mounts under `/api/v1/integration/mail/...`,
> consistent with `/accounts`, `/rules`, `/diagnostics`, `/runtime-metrics`,
> `/report`, `/processed/...`, `/oauth/...`. Re-rooting the controller would
> break every existing endpoint. We kept the existing prefix; **Package B
> must call `/api/v1/integration/mail/provider-presets`**, not
> `/api/v1/mail/automation/provider-presets`.

Returns `200 OK` with a JSON array of 5 `MailProviderPresetResponse` objects
in the order `ALIYUN_QIYE`, `TENCENT_EXMAIL`, `TENCENT_EXMAIL_OVERSEAS`,
`MAIL_263`, `MAIL_263_OVERSEAS`.

### 2.4 Security gate

`@PreAuthorize("hasRole('ADMIN')")` — same gate as every other
account/diagnostics endpoint on `MailAutomationController`. Rationale:

- The presets list is admin-only metadata for the admin "create account"
  form. Non-admins have no UI affordance to consume it.
- Keeping the same gate avoids a one-off security carve-out that future
  reviewers would have to second-guess.

The security tests assert anonymous → 401, USER role → 403, ADMIN → 200.

---

## 3. Verified preset values

| id                       | imapHost                | imapPort | imapSecurity | Source                                                                                                            |
| ------------------------ | ----------------------- | -------- | ------------ | ----------------------------------------------------------------------------------------------------------------- |
| `ALIYUN_QIYE`            | `imap.qiye.aliyun.com`  | 993      | SSL          | https://help.aliyun.com/zh/document_detail/2937082.html — WebFetch returned a usable parse (table row IMAP / 143 / 993). |
| `TENCENT_EXMAIL`         | `imap.exmail.qq.com`    | 993      | SSL          | https://service.rtxmail.net/faq/119.html — primary source, **HTTPS cert expired** (WebFetch failed). Fallback: WebSearch + parse of https://www.cnblogs.com/A121/p/16913163.html ("接收邮件服务器：imap.exmail.qq.com，使用SSL，端口号993"). |
| `TENCENT_EXMAIL_OVERSEAS`| `hwimap.exmail.qq.com`  | 993      | SSL          | Same as above (Tencent primary URL had expired cert). Fallback: WebSearch result corroborating "海外用户 hwimap.exmail.qq.com SSL 993", consistent with Tencent's documented overseas `hw*` prefix scheme (`hwimap` / `hwsmtp`). |
| `MAIL_263`               | `imap.263.net`          | 993      | SSL          | https://download.263.net/263/helpcenter/client/20160603/970.html — WebFetch returned a usable parse (domestic table row imap.263.net / 143 / 993). |
| `MAIL_263_OVERSEAS`      | `imapw.263.net`         | 993      | SSL          | Same URL — overseas table row imapw.263.net / 143 / 993, parsed by the same WebFetch call. |

### Verification fallback notes

- **Tencent (`TENCENT_EXMAIL`, `TENCENT_EXMAIL_OVERSEAS`)**: the spec-listed
  primary URL `https://service.rtxmail.net/faq/119.html` returns
  `certificate has expired` to WebFetch. We additionally tried
  `https://service.exmail.qq.com/cgi-bin/help?...` (now redirects to a JS
  shell on `open.work.weixin.qq.com` and returns no usable text). The IMAP
  values were therefore derived from a WebSearch hit on
  https://www.cnblogs.com/A121/p/16913163.html (which mirrors Tencent's
  official copy verbatim) and the WebSearch summary that explicitly cites
  Tencent's documented `hw`-prefixed overseas hosts. SSL/993 is also the
  Tencent-documented "non-SSL no longer supported" default. We chose 993/SSL
  as the safe, documented default; a dev with a working Tencent test mailbox
  should re-validate during integration smoke if the values ever drift.
- **Aliyun and 263**: WebFetch parsed the primary spec URLs cleanly. Aliyun
  documentation lists ports `143` (plaintext) and `993` (SSL); we picked
  `993/SSL` because the rest of the preset family standardises on SSL and
  Athena's `MailAccount.SecurityType` defaults to SSL.

The constructor of `MailProviderPreset` rejects `OAUTH2` for `imapSecurity`,
so a future regression that tries to repurpose the enum for OAuth providers
fails at class init, not silently in production.

---

## 4. Verification

### Targeted tests

```bash
cd /Users/chouhua/Downloads/Github/Athena-mail-presets-backend/ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=MailAutomationControllerSecurityTest,MailProviderPresetTest test
```

Result (2026-05-07):

```
[INFO] Running com.ecm.core.integration.mail.preset.MailProviderPresetTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ecm.core.integration.mail.controller.MailAutomationControllerSecurityTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Total: **18 / 18 green** in 12 s. Three new cases were added to
`MailAutomationControllerSecurityTest` (anonymous→401, USER→403, ADMIN→200
with the 5 expected presets in order, no credential fields in the response
body); five new cases were added in `MailProviderPresetTest` (constants,
field invariants, no-OAUTH2 rule, DTO mapping, verified specific values).

### Whitespace / hygiene

```bash
cd /Users/chouhua/Downloads/Github/Athena-mail-presets-backend
git diff --check
```

No whitespace errors.

### `mvnw` on this machine

`mvnw` requires Docker and is not available on the current dev machine. Tests
ship through the Surefire `**/*Test.java` glob and will run in CI alongside
the rest of the mail-controller security suite.

---

## 5. Files Changed

New files:

- `ecm-core/src/main/java/com/ecm/core/integration/mail/preset/MailProviderPreset.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/preset/MailProviderPresetResponse.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/preset/MailProviderPresetTest.java`
- `docs/MAIL_PROVIDER_PRESETS_BACKEND_DESIGN_VERIFICATION_20260507.md` (this file)

Modified files:

- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
  - Added imports: `MailProviderPreset`, `MailProviderPresetResponse`, `java.util.Arrays`.
  - Added `GET /provider-presets` admin-only endpoint immediately after `getAccounts()`.
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerSecurityTest.java`
  - Added three test cases for the new endpoint (anonymous, USER, ADMIN).

Untouched (verified):

- `MailAccount` / `MailAccountRequest` / `MailAccountResponse`.
- Any frontend code.
- `.env`, `CLAUDE.md`, top-level meta files.
- Liquibase migrations / DDL.

---

## 6. Remaining Work

- **SMTP presets** — Package C scope. Each provider in this enum has a
  parallel SMTP host (e.g. `smtp.exmail.qq.com:465/SSL`,
  `hwsmtp.exmail.qq.com`, `smtp.263.net` etc.). Package C will decide
  whether to add an `smtpHost`/`smtpPort`/`smtpSecurity` triple to the same
  enum or split it into a separate `MailSmtpProviderPreset`.
- **OAuth presets** — out of scope for this slice. Aliyun/Tencent/263 enterprise
  mailboxes are username/password IMAP. Microsoft 365 / Google OAuth presets
  would belong to a different enum and a different security-disclosure review.
- **Microsoft 365 / Gmail / generic IMAP** — explicitly not in this slice.
- **Frontend integration** — Package B owns wiring the new endpoint into the
  admin "Create Mail Account" form. The contract Package B consumes is
  `GET /api/v1/integration/mail/provider-presets` (admin-only, JSON array of
  five entries with `id`, `label`, `imapHost`, `imapPort`, `imapSecurity`).
- **Credential storage** — preset metadata is static; admins still enter
  username/password manually. There is no preset-driven credential
  pre-population path, by design.
