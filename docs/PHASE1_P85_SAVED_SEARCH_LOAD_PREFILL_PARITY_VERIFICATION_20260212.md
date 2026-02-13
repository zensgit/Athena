# Phase 1 P85 - Saved Search Load Prefill Parity (Verification) - 2026-02-12

## Verification Scope
- Loading a saved search into Advanced Search restores preview status filters.
- Loading a saved search restores folder scope and include-children choice.
- Path input is disabled when folder scope is active.
- Existing dialog search/save flow remains healthy.

## Commands
```bash
cd ecm-frontend
npx eslint src/store/slices/uiSlice.ts src/pages/SavedSearchesPage.tsx e2e/saved-search-load-prefill.spec.ts
ECM_UI_URL=http://localhost:3000 npx playwright test e2e/saved-search-load-prefill.spec.ts e2e/search-dialog-preview-status.spec.ts --reporter=list
```

## Results
- `eslint`: passed.
- Playwright (`saved-search-load-prefill.spec.ts` + `search-dialog-preview-status.spec.ts`): passed (`2 passed`).

## Conclusion
- P85 is verified complete.
- Saved-search load-to-dialog flow now restores preview status and folder scope semantics correctly.
- Regression check confirms preview-status save/search flow remains stable after prefill mapping change.
