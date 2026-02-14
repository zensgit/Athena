# Phase 59 - Search: "Did You Mean" + Save Search Convenience (Verification)

Date: 2026-02-14

## What We Verify

- A misspelled query produces a visible "Did you mean" suggestion.
- Clicking a suggestion updates results (re-runs search with corrected query).
- Saving a search from `Advanced Search` calls the Saved Search create endpoint and closes the save dialog.

## Mocked E2E (No Backend Required)

### Build + Serve

```bash
cd ecm-frontend
npm run build
python3 -m http.server 5500 --directory build
```

### Run Test

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/search-suggestions-save-search.mock.spec.ts \
  --project=chromium --workers=1
```

## Expected Results

- Playwright spec passes.
- UI shows "Did you mean" for the misspelled query.
- UI renders a result for the corrected query after clicking the suggestion.

