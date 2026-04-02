# Phase367Y Verification: Context Compare Direct Lineage Fallback

## Verified

- `Compare with checkout baseline` no longer depends on the baseline version already being present in the loaded history page window.
- `Compare with checkout current` no longer depends on the current version already being present in the loaded history page window.
- Existing compare dialog flow remains unchanged.

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/dialogs/VersionHistoryDialog.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx docs/PHASE367Y_CONTEXT_COMPARE_DIRECT_LINEAGE_FALLBACK_DEV_20260327.md docs/PHASE367Y_CONTEXT_COMPARE_DIRECT_LINEAGE_FALLBACK_VERIFICATION_20260327.md
```
