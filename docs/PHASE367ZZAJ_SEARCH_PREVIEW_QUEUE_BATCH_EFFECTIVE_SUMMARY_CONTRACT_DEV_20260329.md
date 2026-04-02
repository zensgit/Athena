# Phase 367ZZAJ: Search Preview Queue Batch Effective Summary Contract

## Goal

Make `/api/v1/search/preview/queue-failed` return a richer effective preview summary per batch item so the advanced search operator surface can converge immediately after a batch retry/rebuild.

Before this slice, search-scope batch items only carried:

- `previewStatus`
- `queueState`
- `attempts`
- `nextAttemptAt`

That left the frontend with a stale split between:

- updated queue state
- old failure reason/category still coming from the original search result

## Backend

Files:

- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`

Changes:

- Extended `PreviewQueueSearchBatchItemDto` to include:
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`
- Updated queued/skipped/failed batch item construction so:
  - queued items clear stale failure detail when the queue status no longer reports a failure
  - declined/skipped items preserve effective failure detail
  - backend tests assert the richer JSON contract

## Frontend

Files:

- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/utils/previewQueueSearchBatchUtils.ts`
- `ecm-frontend/src/utils/previewQueueSearchBatchUtils.test.ts`

Changes:

- Extended `PreviewQueueSearchBatchItem` typing with effective failure summary fields
- Added shared helper to project batch results into `previewQueueStatusById`
- Updated `AdvancedSearchPage` to use the helper so local overrides now carry:
  - `previewStatus`
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`
  - queue lifecycle data

## Outcome

All-matched preview retry/rebuild actions in advanced search now use a richer local fact source, reducing the lag between:

- queue lifecycle feedback
- effective preview failure semantics
- local failed-preview summaries/chips/tooltips
