# Phase368ZH Advanced Search Queue Mutation Contract Convergence

## Goal

Make `AdvancedSearchPage` consume the shared preview queue mutation contract instead of hand-writing queue override objects.

## Problem

`AdvancedSearchPage.tsx` still built `previewQueueStatusById` objects inline in `handleRetryPreview(...)` and `runPreviewBatchAction(...)`. That duplicated the shared queue contract and risked missing fields such as `previewLastUpdated`, `attempts`, `nextAttemptAt`, and `message`.

## Design

Use the existing `buildPreviewQueueOverride(...)` helper from `utils/previewQueueOverrideUtils` for single-item queue mutations.

Behavioral expectations:

- queue/retry responses write the full shared override payload into `previewQueueStatusById`
- cancel keeps the previous override payload and only updates queue state/message
- existing tooltip/count logic continues to derive from the local override map

## Files

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

## Result

`AdvancedSearchPage` now reuses the same queue mutation builder already used elsewhere in the frontend, reducing local contract drift.
