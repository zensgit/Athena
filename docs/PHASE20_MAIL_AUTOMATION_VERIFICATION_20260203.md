# Phase 20 Mail Automation Verification (2026-02-03)

## Environment
- API: `http://localhost:7700`
- UI: `http://localhost:5500`
- Keycloak: `http://localhost:8180`

## Mail Automation API Validation
```bash
TOKEN=$(cat /tmp/ecm-token.internal)

# Accounts
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:7700/api/v1/integration/mail/accounts

# Rules
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:7700/api/v1/integration/mail/rules

# Trigger fetch
curl -sS -H "Authorization: Bearer $TOKEN" -X POST \
  http://localhost:7700/api/v1/integration/mail/fetch

# Fetch summary
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:7700/api/v1/integration/mail/fetch/summary

# Diagnostics
curl -sS -H "Authorization: Bearer $TOKEN" \
  'http://localhost:7700/api/v1/integration/mail/diagnostics?limit=10'

# Diagnostics export (first lines)
curl -sS -H "Authorization: Bearer $TOKEN" \
  'http://localhost:7700/api/v1/integration/mail/diagnostics/export?limit=5&includeProcessed=true&includeDocuments=true&includeSubject=true&includeError=true&includePath=true&includeMimeType=true&includeFileSize=true' \
  | head -n 6

# Audit check
curl -sS -H "Authorization: Bearer $TOKEN" \
  'http://localhost:7700/api/v1/analytics/audit/recent?limit=30'
```

### Results (summary)
- Account: `gmail-imap` (`OAUTH2`, `oauthConnected=true`, last fetch status `SUCCESS`).
- Rule: `gmail-attachments` (`ATTACHMENTS_ONLY`, folders `ECM-TEST,INBOX`, post-action `MARK_READ`).
- Trigger fetch response: `accounts=1`, `attempted=1`, `found=1`, `matched=0`, `processed=0`, `skipped=1`, `errors=0`.
- Fetch summary endpoint reports the latest run with `fetchedAt` timestamp.
- Diagnostics returns processed mail + document entries (latest attachment ingested recorded).
- Diagnostics export returns CSV header and records.
- Audit log includes `MAIL_DIAGNOSTICS_EXPORTED` event.

## Playwright E2E
> Note: For stable CI/local runs, the UI was built with `REACT_APP_E2E_BYPASS_AUTH=1` and tests ran with `ECM_E2E_SKIP_LOGIN=1`.

Mail automation spec:
```bash
ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts
```
Result: `2 passed`.

Full suite:
```bash
ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test
```
Result: `28 passed (5.1m)`.

### Production build smoke (no bypass)
```bash
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts
```
Result: **failed** (login did not navigate from `/login` to Keycloak in time, `page.waitForURL` timeout).\
Artifacts: `ecm-frontend/test-results/mail-automation-*/test-failed-1.png` and `trace.zip`.

## Outcome
- ✅ Mail fetch summary persists across reload via API.
- ✅ Mail diagnostics, export, and audit logging validated.
- ✅ Playwright E2E suite passes.
