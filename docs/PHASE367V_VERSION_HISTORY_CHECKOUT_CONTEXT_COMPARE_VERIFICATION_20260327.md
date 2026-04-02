# Phase367V Verification: Version History Checkout Context Compare

## Verified

- Version-row context menu now exposes `Compare with checkout baseline`.
- Version-row context menu now exposes `Compare with checkout current`.
- Actions are disabled when the relevant checkout endpoint version is unavailable or when the selected row is already that endpoint.
- Existing compare dialog flow remains unchanged and is reused.

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/dialogs/VersionHistoryDialog.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx docs/PHASE367V_VERSION_HISTORY_CHECKOUT_CONTEXT_COMPARE_DEV_20260327.md docs/PHASE367V_VERSION_HISTORY_CHECKOUT_CONTEXT_COMPARE_VERIFICATION_20260327.md
```

## Notes

- This phase builds on the existing checkout lineage banner and compare flow; it does not change backend contracts.
