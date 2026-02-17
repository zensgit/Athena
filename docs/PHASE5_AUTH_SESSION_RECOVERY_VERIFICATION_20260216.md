# Phase5 Auth Session Recovery Verification (2026-02-16)

## Scope
- Validate `1+2` delivery:
  1. Playwright auth/session recovery coverage.
  2. `authService.refreshToken` transient-failure hardening.

## Code Areas
- `ecm-frontend/src/services/authService.ts`
- `ecm-frontend/src/services/authService.test.ts`
- `ecm-frontend/src/services/api.ts`
- `ecm-frontend/src/services/api.test.ts`
- `ecm-frontend/src/components/auth/Login.tsx`
- `ecm-frontend/src/components/auth/Login.test.tsx`
- `ecm-frontend/src/constants/auth.ts`
- `ecm-frontend/src/index.tsx`
- `ecm-frontend/e2e/auth-session-recovery.mock.spec.ts`
- `scripts/phase5-regression.sh`

## Commands and Results
1. Unit tests
```bash
cd ecm-frontend
npm test -- --watch=false --runInBand src/services/api.test.ts src/services/authService.test.ts src/components/auth/Login.test.tsx
```
- Result: PASS (`3 suites`, `17 tests`)

2. Frontend lint
```bash
cd ecm-frontend
npm run lint
```
- Result: PASS (`0 errors`, `0 warnings`)

3. Frontend build
```bash
cd ecm-frontend
npm run build
```
- Result: PASS (optimized production build generated)

4. Playwright E2E (auth session recovery)
```bash
cd ecm-frontend
PORT=5512
npx serve -s build -l ${PORT}
ECM_UI_URL=http://localhost:${PORT} npx playwright test e2e/auth-session-recovery.mock.spec.ts --project=chromium --workers=1
```
- Result: PASS (`1 passed`)
- Verified behavior:
  - search request receives two `401` responses (initial + one retry)
  - user is redirected to login
  - login page shows `Your session expired. Please sign in again.`

5. Phase5 + Phase6 delivery gate (full flow)
```bash
PW_PROJECT=chromium PW_WORKERS=1 \
ECM_UI_URL_MOCKED=http://localhost:5514 \
ECM_UI_URL_FULLSTACK=http://localhost \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
KEYCLOAK_REALM=ecm \
ECM_E2E_USERNAME=admin \
ECM_E2E_PASSWORD=admin \
./scripts/phase5-phase6-delivery-gate.sh
```
- Result: PASS
  - mocked regression gate: `10 passed`
  - full-stack admin smoke: `1 passed`
  - phase6 mail integration smoke: `1 passed`
  - phase5 search suggestions integration smoke: `1 passed`
  - p1 smoke: `3 passed, 1 skipped` (optional mail preview-run scenario)

## Notes
- During validation, port `5500` had stale static server interference; switched to dedicated ports (`5510+`) for deterministic runs.
- `scripts/phase5-regression.sh` now auto-starts local static SPA server for custom localhost ports (not only `:5500`).
