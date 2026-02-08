# Phase1 P55 Verification: Search Preview Failure Governance

Date: 2026-02-08
Owner: Codex
Environment:
- Frontend workspace: `ecm-frontend`
- UI URL: `http://localhost:5500`
- API URL: `http://localhost:7700`

## Verification Scope

1. Shared preview failure summary utilities and unit tests.
2. Search and Advanced Search retry-governance behavior.
3. Auth test lint compliance needed for CI front-end gate.

## Commands and Results

### 1) Lint

Command:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS

### 2) Targeted unit tests

Command:

```bash
cd ecm-frontend
CI=true npm test -- --watch=false --runInBand --runTestsByPath \
  src/utils/previewStatusUtils.test.ts \
  src/store/slices/nodeSlice.test.ts \
  src/components/auth/Login.test.tsx \
  src/components/auth/PrivateRoute.test.tsx
```

Result: PASS (`4` suites, `31` tests)

### 3) Production build

Command:

```bash
cd ecm-frontend
npm run build
```

Result: PASS (compiled successfully)

### 4) Playwright search regression

Command:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
  e2e/search-preview-status.spec.ts \
  e2e/search-view.spec.ts \
  e2e/search-highlight.spec.ts
```

Result: PASS (`6` tests)

### 5) Playwright core smoke subset

Command:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/ui-smoke.spec.ts \
  --grep "UI smoke: browse \+ upload \+ search \+ copy/move \+ facets \+ delete \+ rules|UI smoke: PDF upload \+ search \+ version history \+ preview"
```

Result: PASS (`2` tests)

## Functional Outcomes Confirmed

1. Search page now shows failed preview counters (total/retryable/unsupported).
2. Retry controls are hidden when all current-page failures are unsupported.
3. Advanced Search mirrors the same retry-governance behavior and reason formatting.
4. Auth test files satisfy `testing-library/no-wait-for-multiple-assertions` lint rule.

## Notes

1. `PrivateRoute` test run logs one expected `console.error` from mocked auto-login failure path; tests pass and behavior is unchanged.

