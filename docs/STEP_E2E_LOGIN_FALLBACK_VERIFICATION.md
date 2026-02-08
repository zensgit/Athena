# Step: E2E Login Fallback Stabilization (Verification)

## Verification Goal
- Confirm preview-status E2E remains green after introducing bypass-or-login fallback navigation.

## Executed Validation

1. Playwright targeted regression
- Command:
  - `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-preview-status.spec.ts --workers=1`
- Result:
  - `3 passed`
  - `0 failed`

2. Frontend unit (auth/bootstrap and preview util)
- Commands:
  - `cd ecm-frontend && npm test -- --watch=false --runInBand src/components/layout/AppErrorBoundary.test.tsx src/components/auth/Login.test.tsx src/components/auth/PrivateRoute.test.tsx src/services/authBootstrap.test.ts src/constants/auth.test.ts`
  - `cd ecm-frontend && npm test -- --watch=false --runInBand src/utils/previewStatusUtils.test.ts`
- Result:
  - auth/bootstrap set: `5 suites passed, 32 tests passed`
  - preview util: `1 suite passed, 7 tests passed`

3. Backend classifier sanity
- Command:
  - `cd ecm-core && mvn -Dtest=PreviewFailureClassifierTest test`
- Result:
  - `BUILD SUCCESS`
  - `Tests run: 5, Failures: 0, Errors: 0`

## Outcome
- Search preview status suite is stable in local environment with fallback auth handling.
- Unsupported preview behavior remains validated:
  - card label shows unsupported status
  - card-level retry actions are hidden for unsupported cases
