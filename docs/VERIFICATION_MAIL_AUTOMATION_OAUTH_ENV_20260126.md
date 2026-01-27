# Verification: Mail Automation OAuth Env Credentials (2026-01-27)

## Environment
- UI: `http://localhost:3000`
- API: `http://localhost:7700`

## Test run
```bash
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts e2e/ui-smoke.spec.ts -g "Mail automation"
```

## Results
- `e2e/mail-automation.spec.ts` - Mail automation test connection and fetch summary: **passed**
- `e2e/ui-smoke.spec.ts` - Mail automation actions: **passed**
- Overall: **2 passed** (31.9s)

## Targeted e2e
```bash
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts
```
Result:
- `mail-automation.spec.ts`: **1 passed** (10.7s)

## Backend tests
```bash
cd ecm-core
mvn -q -DskipITs test
```
Result:
- Maven test run: **passed** (exit code 0)
- Includes new `MailAutomationControllerTest`

## Full Playwright regression
```bash
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test
```
Result:
- Playwright suite: **21 passed** (5.3m)

## API checks
```bash
# Test connection
POST /api/v1/integration/mail/accounts/{id}/test
```
Result:
- `success=true`
- `message=Connected`

```bash
# Trigger fetch
POST /api/v1/integration/mail/fetch
```
Result:
- `accountErrors=0`
- `processedMessages=0`

## OAuth env self-check
```bash
GET /api/v1/integration/mail/accounts
```
Result (for `gmail-imap`):
- `oauthEnvConfigured=true`
- `oauthMissingEnvKeys=[]`

## Notes
- Tests assume at least one mail account exists; otherwise they skip.
- OAuth env vars are required before connection/fetch can succeed.
- `.env` has been filled and `ecm-core` recreated; connection test now succeeds.
- UI now surfaces missing env keys via `oauthEnvConfigured` and `oauthMissingEnvKeys`.
- `mail-automation.spec.ts` now asserts the `OAuth env missing` warning based on API data.
