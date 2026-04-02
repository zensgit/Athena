# Phase367ZC Checkout Destination Navigation Verification

## Scope

- `VersionHistoryDialog` destination navigation affordance
- `AdvancedSearchPage` destination summary consumption

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/dialogs/VersionHistoryDialog.tsx src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  docs/PHASE367ZC_CHECKOUT_DESTINATION_NAVIGATION_DEV_20260327.md \
  docs/PHASE367ZC_CHECKOUT_DESTINATION_NAVIGATION_VERIFICATION_20260327.md
```

## Result

- ESLint passed for the touched frontend files.
- Frontend production build passed.
- Targeted `git diff --check` passed.

## Notes

- This phase intentionally reuses existing graph data and adds no backend risk.
- The navigation target remains virtual-contract-backed and derived from `destinationNode`.
