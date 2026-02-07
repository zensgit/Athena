# Phase 1 P46 - Auth Init Status Feedback Verification (2026-02-07)

## Summary
- Status: `PASS`
- Focus: make auth bootstrap timeout/error visible on login screen.

## Automated Verification

### 1) Unit tests
- Command:
```bash
cd ecm-frontend
npm test -- --watch=false --runInBand \
  src/services/authBootstrap.test.ts \
  src/components/auth/PrivateRoute.test.tsx \
  src/components/auth/Login.test.tsx
```
- Result:
  - Test Suites: `3 passed`
  - Tests: `14 passed`
  - Failures: `0`

### 2) Playwright smoke
- Command:
```bash
cd ecm-frontend
npx playwright test e2e/ui-smoke.spec.ts --workers=1 --grep "UI smoke: browse \\+ upload \\+ search \\+ copy/move \\+ facets \\+ delete \\+ rules"
```
- Result:
  - `1 passed`
  - `0 failed`

## Behavior Checks
- Login page shows timeout warning when bootstrap status is `timeout`.
- Login page shows generic initialization warning when bootstrap status is `error`.
- Status marker is cleaned after display and on next login attempt.
- Existing core smoke flow remains green.

## Files Verified
- `ecm-frontend/src/constants/auth.ts`
- `ecm-frontend/src/index.tsx`
- `ecm-frontend/src/components/auth/Login.tsx`
- `ecm-frontend/src/components/auth/Login.test.tsx`
