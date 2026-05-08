# SMTP Preset + Test SMTP Integration Verification (2026-05-07)

## Scope

Integrated the three local Claude SMTP packages into `main` in dependency order:

| Package | Source branch | Source commit | Integrated main commit | Scope |
|---|---|---:|---:|---|
| A | `claude/smtp-backend` | `e72baaa` | `82db319` | Extend mail provider presets with SMTP defaults; add admin `POST /api/v1/admin/email/test-smtp`. |
| B | `claude/smtp-ui` | `b0152f7` | `2821a4f` | Show SMTP preset defaults in the mail account dialog; add the Mail Automation `Test SMTP` dialog. |
| C | `claude/smtp-docs` | `9784bfa` | `056086f` | Add SMTP operator runbook, mail-provider doc updates, and site-invitation live-send closeout. |

The local `.env` change was intentionally left untouched and unstaged.

## Integrated Contract

### Provider presets

`GET /api/v1/integration/mail/provider-presets` now returns IMAP and SMTP metadata for five Chinese enterprise mailbox presets:

| id | SMTP host | Port | Security |
|---|---|---:|---|
| `ALIYUN_QIYE` | `smtp.qiye.aliyun.com` | 465 | `SSL` |
| `TENCENT_EXMAIL` | `smtp.exmail.qq.com` | 465 | `SSL` |
| `TENCENT_EXMAIL_OVERSEAS` | `hwsmtp.exmail.qq.com` | 465 | `SSL` |
| `MAIL_263` | `smtp.263.net` | 465 | `SSL` |
| `MAIL_263_OVERSEAS` | `smtpw.263.net` | 465 | `SSL` |

The frontend renders these values as read-only guidance because SMTP is application-level `spring.mail.*` configuration, not per-mail-account state.

### Test SMTP endpoint

`POST /api/v1/admin/email/test-smtp`

Request:

```json
{ "to": "operator@example.com" }
```

Response:

```json
{
  "ok": true,
  "message": "Test message dispatched",
  "smtpHost": "smtp.example.com",
  "smtpPort": 465,
  "fromAddress": "noreply@example.com",
  "diagnostic": null
}
```

`smtpHost`, `smtpPort`, and `fromAddress` are treated as nullable on the frontend because the backend can legally surface missing or unparseable runtime configuration while returning structured diagnostics.

## Codex Review Fix

Claude's UI package typed the Test SMTP response metadata as non-null:

```ts
smtpHost: string;
smtpPort: number;
fromAddress: string;
```

The backend service reads `spring.mail.host`, parses `spring.mail.port`, and reads `ecm.email.from-address` from `Environment`; missing host or unparseable port can be returned as `null` on failure paths. Codex corrected the frontend wire type to:

```ts
smtpHost: string | null;
smtpPort: number | null;
fromAddress: string | null;
```

The success Alert now renders fallback labels (`configured host`, `default port`, `configured sender`) instead of showing `null:null`, and `MailAutomationPage.testSmtp.test.tsx` includes a regression test for that boundary.

## Verification

### Backend targeted tests

```bash
cd /Users/chouhua/Downloads/Github/Athena/ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=MailProviderPresetTest,MailAutomationControllerSecurityTest,EmailAdminTestServiceTest,EmailAdminControllerSecurityTest test
```

Result:

| Suite | Tests | Failures | Errors |
|---|---:|---:|---:|
| `MailProviderPresetTest` | 6 | 0 | 0 |
| `MailAutomationControllerSecurityTest` | 13 | 0 | 0 |
| `EmailAdminTestServiceTest` | 6 | 0 | 0 |
| `EmailAdminControllerSecurityTest` | 4 | 0 | 0 |
| **Total** | **29** | **0** | **0** |

### Frontend targeted tests

```bash
cd /Users/chouhua/Downloads/Github/Athena/ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/pages/MailAutomationPage.presets.test.tsx \
  src/pages/MailAutomationPage.testSmtp.test.tsx \
  --watchAll=false
```

Result: `Test Suites: 2 passed, 2 total. Tests: 15 passed, 15 total.`

### Frontend lint

```bash
cd /Users/chouhua/Downloads/Github/Athena/ecm-frontend
npm run lint
```

Result: clean.

### Frontend CI build

```bash
cd /Users/chouhua/Downloads/Github/Athena/ecm-frontend
CI=true npm run build
```

Result: `Compiled successfully.` CRA still prints the pre-existing bundle-size advisory.

### Whitespace

```bash
git diff --check
```

Result: clean.

## CI Verification

Pushed to `origin/main` at commit `dd7390a` and monitored CI run `25530595299`.

| Job | Result | Duration |
|---|---|---:|
| Backend Verify | success | 2m17s |
| Frontend Build & Test | success | 9m32s |
| Phase C Security Verification | success | 5m08s |
| Frontend E2E Core Gate | success | 13m17s |
| Property Encryption Closeout Gate | success | 4m44s |
| Phase 5 Mocked Regression Gate | success | 6m24s |
| Acceptance Smoke (3 admin pages) | success | 7m29s |

Run conclusion: `success` (7/7 jobs green).

The only annotation was GitHub Actions' platform warning that Node.js 20 actions
are deprecated. It is not a code or product regression for this slice.

## Remaining Work

- Runtime SMTP smoke still requires real deployment credentials and a real recipient inbox; local unit tests intentionally do not commit or expose SMTP secrets.
- A Phase 5 Mocked route for `/admin/email/test-smtp` is still useful so the mocked browser gate asserts this surface directly instead of relying only on the defensive shape guard.
- Site invitation live-send remains documented as an operator smoke because delivery depends on `ECM_EMAIL_ENABLED=true`, public `ECM_FRONTEND_BASE_URL`, and provider SMTP credentials.
