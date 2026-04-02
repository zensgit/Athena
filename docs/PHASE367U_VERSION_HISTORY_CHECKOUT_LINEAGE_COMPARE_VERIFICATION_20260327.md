# Phase367U Verification: Version History Checkout Lineage Compare

## Verified

- `VersionHistoryDialog` loads active checkout relation metadata.
- Checked-out documents show an explicit checkout lineage banner.
- When baseline and current versions are both loaded, operators can launch a prefilled compare for checkout lineage in one click.

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/dialogs/VersionHistoryDialog.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx ecm-frontend/src/services/nodeService.ts docs/PHASE367U_VERSION_HISTORY_CHECKOUT_LINEAGE_COMPARE_DEV_20260327.md docs/PHASE367U_VERSION_HISTORY_CHECKOUT_LINEAGE_COMPARE_VERIFICATION_20260327.md
```

## Notes

- This phase still depends on loaded version history containing both baseline and current versions.
- This phase does not yet create a full working-copy node relationship graph.
