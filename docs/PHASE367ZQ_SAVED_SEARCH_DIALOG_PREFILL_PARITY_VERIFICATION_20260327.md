# Phase367ZQ Saved Search Dialog Prefill Parity Verification

## Scope

- `SavedSearchesPage` `Load to Search` prefill payload
- lock/checkout prefill parity for `SearchDialog`

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/SavedSearchesPage.tsx
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/pages/SavedSearchesPage.tsx \
  docs/PHASE367ZQ_SAVED_SEARCH_DIALOG_PREFILL_PARITY_DEV_20260327.md \
  docs/PHASE367ZQ_SAVED_SEARCH_DIALOG_PREFILL_PARITY_VERIFICATION_20260327.md
```

## Result

- Frontend ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
