# Phase 1 P84 - Advanced Search Dialog Validation Hint (Verification) - 2026-02-12

## Verification Scope
- Advanced Search modal shows criteria-required hint when no criteria are selected.
- `Save Search` and `Search` are disabled for empty criteria.
- Selecting a valid criterion enables both actions and removes the hint.
- Existing preview-status save/search flow remains functional.

## Commands
```bash
cd ecm-frontend
npx eslint src/components/search/SearchDialog.tsx e2e/search-dialog-preview-status.spec.ts
ECM_UI_URL=http://localhost:3000 npx playwright test e2e/search-dialog-preview-status.spec.ts --reporter=list
```

## Results
- `eslint`: passed.
- Playwright (`search-dialog-preview-status.spec.ts`): passed (`1 passed`).

## Conclusion
- P84 is verified complete.
- Advanced Search dialog now provides explicit guidance when no criteria are set.
- Action state behavior is consistent and test-covered:
  - empty criteria => `Save Search` and `Search` disabled + hint visible
  - valid criteria present => both actions enabled + hint removed
