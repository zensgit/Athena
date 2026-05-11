# SMTP Test - Phase 5 Mocked Route Design and Verification

Date: 2026-05-11

## Context

The SMTP preset and test-smtp work shipped a backend endpoint:

```text
POST /api/v1/admin/email/test-smtp
```

The frontend already had both service-level and page-level shape guards for
the Phase 5 mocked HTML-fallback case, but the mocked browser gate did not
exercise the successful `test-smtp` path directly. The closeout docs therefore
kept a follow-up item to add a mocked route for this endpoint.

This slice closes that test-harness gap.

## Design

`mail-automation-phase6-p1.mock.spec.ts` now mocks two mail-admin runtime
dependencies:

| Route | Response |
|---|---|
| `GET /api/v1/integration/mail/provider-presets` | Valid preset JSON with IMAP and SMTP metadata |
| `POST /api/v1/admin/email/test-smtp` | Valid `EmailTestSmtpResponse` with `ok=true` |

The browser test now opens the real `Test SMTP` dialog from
`MailAutomationPage`, fills `ops@example.com`, submits the form, and asserts:

- the mocked API route is called exactly once;
- the request body is `{ "to": "ops@example.com" }`;
- the success alert renders the mocked SMTP host;
- the success alert renders the mocked sender address;
- the dialog closes cleanly from the success state.

This does not replace the defensive HTML-fallback shape guards. Those remain
useful runtime protection. The difference is that the Phase 5 mocked gate now
also pins the intended successful contract.

No product code, backend endpoint, DTO, migration, or service method changed.

## Verification

### Local gates

| Gate | Command | Result |
|---|---|---|
| Phase 5 mocked gate | `CI=false PW_WORKERS=1 PW_PROJECT=chromium bash scripts/phase5-regression.sh` | 31 tests passed, 4.4m |
| Frontend build | included in `scripts/phase5-regression.sh` | compiled successfully; CRA bundle-size advisory remains informational |
| Whitespace | `git diff --check` | clean |

The modified spec was included in the full mocked gate:

```text
e2e/mail-automation-phase6-p1.mock.spec.ts ... passed (12.9s)
```

The gate also reported all 24 expected recovery events observed and no startup
SLA warnings.

### GitHub Actions

Run: `25661651542`

| Job | Result | Duration |
|---|---|---|
| Backend Verify | passed | 2m21s |
| Frontend Build & Test | passed | 10m10s |
| Phase C Security Verification | passed | 5m12s |
| Acceptance Smoke (3 admin pages) | passed | 6m36s |
| Phase 5 Mocked Regression Gate | passed | 6m22s |
| Frontend E2E Core Gate | passed | 11m35s |
| Property Encryption Closeout Gate | passed | 4m0s |

## Files Changed

- `ecm-frontend/e2e/mail-automation-phase6-p1.mock.spec.ts`
- `docs/SMTP_PRESET_TEST_INTEGRATION_VERIFICATION_20260507.md`
- `docs/SMTP_PRESET_AND_TEST_UI_DESIGN_VERIFICATION_20260507.md`
- `docs/SMTP_TEST_SMTP_PHASE5_MOCKED_ROUTE_DESIGN_VERIFICATION_20260511.md`

## Remaining Work

- Runtime SMTP smoke still requires real deployment credentials and a real
  recipient inbox; this mocked route only verifies the browser/API contract.
- The defensive HTML-fallback shape guards should stay in place because any
  future unmocked route can still return the SPA shell in mocked/static runs.
