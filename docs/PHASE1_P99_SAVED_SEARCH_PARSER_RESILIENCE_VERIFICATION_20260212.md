# Phase 1 P99 Verification: Saved Search Parser Resilience

Date: 2026-02-12

## Environment

- UI: `http://localhost:3000`
- API: `http://localhost:7700`

## Validation Commands

1. Unit tests

```bash
cd ecm-frontend
CI=true npm test -- --watch=false src/utils/savedSearchUtils.test.ts src/utils/searchPrefillUtils.test.ts
```

Result:
- PASS (`2 suites`, `10 tests`)

2. Lint

```bash
cd ecm-frontend
npx eslint \
  src/utils/savedSearchUtils.ts \
  src/utils/savedSearchUtils.test.ts
```

Result:
- PASS

3. Playwright regression for saved-search load path

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
  e2e/saved-search-load-prefill.spec.ts \
  e2e/search-dialog-active-criteria-summary.spec.ts \
  e2e/search-dialog-preview-status.spec.ts \
  --reporter=list
```

Result:
- PASS (`12 passed`)

## New Unit Coverage

- `ignores malformed fields while preserving valid criteria values`
- `degrades gracefully when queryParams JSON is malformed`

## Outcome

- Parser now ignores invalid dates, invalid size values, malformed list entries, and malformed JSON safely.
- Valid criteria continue to load and prefill correctly.
