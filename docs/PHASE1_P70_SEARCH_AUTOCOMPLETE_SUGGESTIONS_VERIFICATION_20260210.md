# Phase 1 P70 - Search Autocomplete Suggestions (Verification) (2026-02-10)

## Scope
Verify the Search Results page "Quick search by name..." input:

- shows suggestions via `/api/v1/search/suggestions`
- selecting a suggestion updates the input and returns the expected document in results

## Environment
- UI: `ECM_UI_URL=http://localhost:5500`
- API: `ECM_API_URL=http://localhost:7700`

## Automated Verification (Playwright)
From `ecm-frontend/`:

```bash
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-autocomplete-suggestions.spec.ts
```

Expected:
- ✅ test passes

## Full Regression (Recommended)
From `ecm-frontend/`:

```bash
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test
```

Expected:
- ✅ all tests pass (some suites may be marked skipped depending on local mail/OAuth setup)

