# Phase 1 P105 Verification: Search Fallback Criteria-Key Parity

Date: 2026-02-12

## Scope

Verify that:
- SearchResults fallback governance can engage even when the query changes (criteria key ignores query),
- AdvancedSearch fallback governance behaves the same,
- Exact/high-precision queries are suppressed by default but support explicit opt-in reveal.

## Commands

### Frontend lint (targeted)

```bash
cd ecm-frontend
npm run lint -- \
  src/pages/SearchResults.tsx \
  src/pages/AdvancedSearchPage.tsx \
  src/utils/searchFallbackUtils.ts \
  e2e/search-fallback-governance.spec.ts \
  e2e/advanced-search-fallback-governance.spec.ts
```

### Playwright E2E (governance specs)

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
  e2e/search-fallback-governance.spec.ts \
  e2e/advanced-search-fallback-governance.spec.ts \
  --reporter=line
```

## Results

- Lint: PASS
- Playwright: PASS (`5 passed`)

## Manual sanity checks (optional)

1. Perform a normal search (returns results).
2. Search for an exact/binary-like filename that returns 0 (e.g. `*.bin`).
3. Confirm:
   - notice: previous results hidden by default
   - action: "Show previous results" reveals them
   - action: "Retry" re-runs search

