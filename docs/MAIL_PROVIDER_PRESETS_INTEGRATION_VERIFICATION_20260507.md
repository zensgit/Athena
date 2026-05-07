# Mail Provider Presets Integration Verification

Date: 2026-05-07

## Context

Claude delivered three local branches for Chinese enterprise mailbox presets:

- `claude/mail-presets-backend` at `77a8452`
- `claude/mail-presets-ui` at `41617d3`
- `claude/mail-presets-docs` at `d9e6361`

Codex integrated the branches onto `main` in A -> B -> C order and reviewed the cross-boundary contract before push.

## Integrated Commits

| Commit | Scope |
| --- | --- |
| `9afe822` | Backend enum/DTO and `GET /api/v1/integration/mail/provider-presets` admin endpoint |
| `6d60ee8` | Mail Automation UI preset dropdown and scoped tests |
| `78f6125` | Frontend service fix to use `/integration/mail/provider-presets` under the shared `/api/v1` prefix |
| `a4bdc49` | Aliyun / Tencent / 263 operator setup guide |

## Review Findings Fixed During Integration

| Finding | Risk | Fix |
| --- | --- | --- |
| UI test fixture used `imap.263xmail.com` for `MAIL_263_OVERSEAS` while the backend enum, backend tests, and operator doc use `imapw.263.net`. | A future test could normalize a wrong 263 overseas host value. | Updated the fixture to `imapw.263.net`. |
| Operator doc referenced a `MAIL_AUTOMATION_ADMIN` role that does not exist in the current controller gate. | Operators would look for the wrong role when debugging `403` responses. | Reworded to `ROLE_ADMIN` and cited the actual `@PreAuthorize("hasRole('ADMIN')")` gate. |
| Operator doc described `{id}` as numeric, but `MailAccount.id` is a UUID. | Manual smoke commands could mislead operators. | Reworded the checklist to say UUID. |
| Operator doc described a per-account "Fetch now" action and diagnostics capability/watermark fields not present in `MailAutomationController` / `MailFetcherService.MailDiagnosticsResult`. | The runbook would promise fields/actions the product does not expose. | Reworded the checklist to the existing global fetch endpoint and current diagnostics payload fields. |
| Post-push CI `Phase 5 Mocked Regression Gate` returned HTML `200` from the static dev server for the unmocked `/api/v1/integration/mail/provider-presets` path. | The page accepted the non-array payload, then `providerPresets.map(...)` triggered the React error boundary in existing mail automation mocked specs. | Guarded the preset response with `Array.isArray(...)`, added a malformed-response regression test, and reran the full mocked gate locally. |

## Contract Check

Backend source of truth:

```text
GET /api/v1/integration/mail/provider-presets
```

Frontend service call:

```text
api.get('/integration/mail/provider-presets')
```

The shared frontend API client supplies the `/api/v1` prefix, so the runtime request matches the backend mount.

Preset rows after integration:

| id | imapHost | imapPort | imapSecurity |
| --- | --- | --- | --- |
| `ALIYUN_QIYE` | `imap.qiye.aliyun.com` | 993 | SSL |
| `TENCENT_EXMAIL` | `imap.exmail.qq.com` | 993 | SSL |
| `TENCENT_EXMAIL_OVERSEAS` | `hwimap.exmail.qq.com` | 993 | SSL |
| `MAIL_263` | `imap.263.net` | 993 | SSL |
| `MAIL_263_OVERSEAS` | `imapw.263.net` | 993 | SSL |

## Verification Results

Executed after the integration fixup from `/Users/chouhua/Downloads/Github/Athena`.

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=MailAutomationControllerSecurityTest,MailProviderPresetTest test
```

Result: passed. Maven exited `0`; target scope covers `MailAutomationControllerSecurityTest` plus `MailProviderPresetTest` after adding the provider-presets endpoint assertions.

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/pages/MailAutomationPage.presets.test.tsx --watchAll=false
```

Result: passed. `6 / 6` tests passed in `MailAutomationPage.presets.test.tsx`.

Follow-up after the first CI run exposed the mocked-gate non-array payload path:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/pages/MailAutomationPage.presets.test.tsx --watchAll=false
```

Result: passed. `7 / 7` tests passed in `MailAutomationPage.presets.test.tsx`, including the malformed `provider-presets` payload regression.

```bash
cd ecm-frontend
npm run lint
```

Result: passed. ESLint exited `0`.

```bash
cd ecm-frontend
CI=true npm run build
```

Result: passed. CRA compiled successfully; the existing bundle-size advisory remains informational.

```bash
git diff --check
```

Result: passed. No whitespace errors.

```bash
CI=false bash scripts/phase5-regression.sh
```

Result: passed. `31 / 31` Playwright mocked Phase 5 tests passed, including:

- `e2e/mail-automation-trigger-fetch.mock.spec.ts`
- `e2e/mail-automation-diagnostics-export.mock.spec.ts`
- `e2e/mail-automation-processed-management.mock.spec.ts`
- `e2e/mail-automation-phase6-p1.mock.spec.ts`

GitHub Actions run `25501275725` before this follow-up fix was `5 / 6` green: Backend Verify, Frontend Build & Test, Phase C Security Verification, Property Encryption Closeout Gate, Frontend E2E Core Gate, and Acceptance Smoke all succeeded; only `Phase 5 Mocked Regression Gate` failed for the malformed static-server fallback described above.

## Remaining Work

- Live mailbox smoke still requires real Aliyun / Tencent / 263 test accounts and provider-side client authorization codes.
- SMTP is documented for operators but remains out of product scope; Athena Mail Automation is still IMAP-fetch only in this slice.
- Provider host values can drift over time; if a live smoke fails while credentials and IMAP enablement are correct, re-check the provider documentation and update `MailProviderPreset`.
