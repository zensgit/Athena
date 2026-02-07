# Phase 1 P52 - Auth Redirect Policy Config Verification (2026-02-07)

## Summary
- Status: `PASS`
- Focus: configurable redirect-failure policy and parser safety.

## Automated Verification

### 1) Unit tests
- Command:
```bash
cd ecm-frontend
npm test -- --watch=false --runInBand \
  src/constants/auth.test.ts \
  src/components/auth/PrivateRoute.test.tsx \
  src/components/auth/Login.test.tsx \
  src/services/authBootstrap.test.ts
```
- Result:
  - Test Suites: `4 passed`
  - Tests: `29 passed`
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
- Invalid env values fall back safely.
- Positive values are accepted and normalized.
- Pause message shows failure count/limit details.
- Core browse/upload/search smoke remains stable.

## Verified Files
- `ecm-frontend/src/constants/auth.ts`
- `ecm-frontend/src/constants/auth.test.ts`
- `ecm-frontend/src/components/auth/Login.tsx`
- `ecm-frontend/src/components/auth/Login.test.tsx`
