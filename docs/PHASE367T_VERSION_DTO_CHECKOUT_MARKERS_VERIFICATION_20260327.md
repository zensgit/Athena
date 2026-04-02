# Phase367T Verification: Version DTO Checkout Markers

## Verified

- Version DTOs now expose `checkoutBaseline` and `checkoutCurrent`.
- Node relation versions API returns those markers.
- `AdvancedSearchPage` relation details render version entries with baseline/current markers.
- `VersionHistoryDialog` shows `Baseline` and `Current` chips and compare-picker labels.

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeControllerRelationsTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts src/components/dialogs/VersionHistoryDialog.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-core/src/main/java/com/ecm/core/dto/VersionDto.java ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java ecm-frontend/src/types/index.ts ecm-frontend/src/services/nodeService.ts ecm-frontend/src/pages/AdvancedSearchPage.tsx ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx docs/PHASE367T_VERSION_DTO_CHECKOUT_MARKERS_DEV_20260327.md docs/PHASE367T_VERSION_DTO_CHECKOUT_MARKERS_VERIFICATION_20260327.md
```

## Notes

- This phase still does not create separate working-copy nodes.
- This phase still does not expose a bidirectional working-copy/source graph.
- This phase focuses on version-level semantic markers rather than entity-level checkout forks.
