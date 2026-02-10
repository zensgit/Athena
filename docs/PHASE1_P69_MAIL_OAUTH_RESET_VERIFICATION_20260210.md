# Phase 1 P69 - Mail OAuth Reset + OAuth Skip Strategy (Verification)

Date: 2026-02-10

## Scope Verified

- Backend: OAuth reset endpoint + OAuth skip strategy for scheduled fetch and debug fetch.
- Frontend: Reset OAuth action is available for OAuth2 accounts and triggers backend call.
- E2E: Playwright spec validates reset flow in an "e2e safe" way (no external OAuth login required).

## Commands

### Backend (unit/compile)

```bash
cd ecm-core
mvn -q -Dtest=MailOAuthTokenErrorParserTest test
```

### Frontend (Playwright E2E)

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/mail-automation.spec.ts
```

## Results

- Backend tests:
  - `mvn -q -Dtest=MailOAuthTokenErrorParserTest test` -> PASS
- Playwright E2E:
  - `npx playwright test e2e/mail-automation.spec.ts` -> PASS (8 passed, 4 skipped)

## Notes

- The E2E suite targets `ECM_UI_URL=http://localhost:5500` (Docker-served frontend). After changing frontend code, rebuild/recreate the frontend container before re-running E2E:

```bash
bash scripts/restart-ecm.sh
```
