# Phase 1 P97 Verification: Saved Search JSON Filter + Alias Normalization

Date: 2026-02-12

## Environment

- Frontend dev: `http://localhost:3000`
- API: `http://localhost:7700`
- Browser tests: Playwright (chromium)

Note:
- `http://localhost:5500` points to a deployed static build and may not include current branch code.
- Verification for this change set was run against local dev server `3000`.

## Validation Commands

1. Unit tests

```bash
cd ecm-frontend
CI=true npm test -- --watch=false src/utils/savedSearchUtils.test.ts
```

Result:
- PASS (`5 passed`)

2. Lint (changed files)

```bash
cd ecm-frontend
npx eslint src/utils/savedSearchUtils.ts src/utils/savedSearchUtils.test.ts e2e/saved-search-load-prefill.spec.ts
```

Result:
- PASS

3. Playwright regression (saved-search + search dialog continuity)

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
- PASS (`11 passed`)

## New Coverage Added

- `savedSearchUtils.test.ts`
  - `supports JSON-string queryParams and filter aliases with status normalization`
- `saved-search-load-prefill.spec.ts`
  - `load action normalizes JSON-string filters and preview status aliases`

## Outcome

- Saved-search load path now correctly parses JSON-string filters and additional legacy aliases.
- Preview status aliases are normalized to canonical values and reflected in advanced dialog selections.
- Existing advanced dialog prefill and preview-status regression suites remain stable.
