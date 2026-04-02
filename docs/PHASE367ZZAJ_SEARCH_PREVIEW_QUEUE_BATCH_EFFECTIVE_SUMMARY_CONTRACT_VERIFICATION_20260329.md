# Phase 367ZZAJ: Search Preview Queue Batch Effective Summary Contract Verification

## Verification

Backend:

```bash
cd ecm-core
mvn -q -Dtest='SearchControllerTest#queueFailedPreviewsBySearchShouldReturnOk+queueFailedPreviewsBySearchShouldReturnDeclinedQueueStateWhenSkipped+queueFailedPreviewsBySearchSkipsUnsupportedEffectiveFailureCategory' test
```

Frontend:

```bash
cd ecm-frontend
./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts src/utils/previewQueueSearchBatchUtils.ts src/utils/previewQueueSearchBatchUtils.test.ts
CI=true npm test -- --watch=false --runInBand src/utils/previewQueueSearchBatchUtils.test.ts
npm run -s build
```

Diff hygiene:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/SearchController.java \
  ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java \
  ecm-frontend/src/services/nodeService.ts \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  ecm-frontend/src/utils/previewQueueSearchBatchUtils.ts \
  ecm-frontend/src/utils/previewQueueSearchBatchUtils.test.ts \
  docs/PHASE367ZZAJ_SEARCH_PREVIEW_QUEUE_BATCH_EFFECTIVE_SUMMARY_CONTRACT_DEV_20260329.md \
  docs/PHASE367ZZAJ_SEARCH_PREVIEW_QUEUE_BATCH_EFFECTIVE_SUMMARY_CONTRACT_VERIFICATION_20260329.md
```

## Expected Assertions

- queued search-scope batch items return `previewLastUpdated`
- skipped/declined batch items preserve effective failure reason/category
- frontend local preview queue overrides clear stale failure detail for newly queued items
