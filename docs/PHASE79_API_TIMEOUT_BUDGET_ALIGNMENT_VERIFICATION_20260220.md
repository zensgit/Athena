# Phase 79: API Timeout Budget Alignment Verification

## Date
2026-02-20

## Scope
- Verify timeout budget behavior and timeout recovery unit paths.
- Verify auth-route startup matrix is unaffected.
- Verify mocked regression gate is green after timeout and watchdog test updates.

## Commands and Results

1. Targeted unit tests (API timeout + auth boot)
```bash
cd ecm-frontend
npm test -- --watch=false --runInBand \
  src/services/api.test.ts \
  src/services/authBootstrap.test.ts \
  src/components/auth/AuthBootingScreen.test.tsx
```
- Result: PASS (`3 passed`, `18 passed`)

2. Frontend lint
```bash
cd ecm-frontend
npm run lint
```
- Result: PASS

3. FileBrowser watchdog mocked scenario (updated)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost \
npx playwright test e2e/filebrowser-loading-watchdog.mock.spec.ts --project=chromium --workers=1
```
- Result: PASS (`1 passed`)

4. Auth/route matrix smoke
```bash
bash scripts/phase70-auth-route-matrix-smoke.sh
```
- Result: PASS (`6 passed`)

5. Mocked regression gate
```bash
bash scripts/phase5-regression.sh
```
- Result: PASS (`15 passed`)

## Conclusion
- API timeout budgets are aligned by operation class with env-configurable defaults.
- Timeout recovery now retries safe requests once and surfaces explicit timeout warning on terminal timeout.
- Startup/auth and mocked regression baselines remain green.
