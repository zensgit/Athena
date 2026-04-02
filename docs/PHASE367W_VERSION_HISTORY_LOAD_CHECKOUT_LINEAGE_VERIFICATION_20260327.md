# Phase367W Verification: Version History Load Checkout Lineage

## Verified

- Active checkout banner now exposes `Load checkout lineage versions` when baseline/current versions are not both loaded yet.
- The action keeps paging version history until both lineage endpoints are present or history is exhausted.
- Existing lineage compare flow continues to work once the needed versions are loaded.

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/dialogs/VersionHistoryDialog.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx docs/PHASE367W_VERSION_HISTORY_LOAD_CHECKOUT_LINEAGE_DEV_20260327.md docs/PHASE367W_VERSION_HISTORY_LOAD_CHECKOUT_LINEAGE_VERIFICATION_20260327.md
```

## Notes

- This phase still relies on paged version history availability.
- This phase does not change backend APIs.
