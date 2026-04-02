# Phase368ZG Document Preview Queue Mutation Contract Convergence

## Goal

Close the remaining frontend seam in `DocumentPreview` so queue mutations immediately honor the full queue response contract, including `previewStatus`, `previewFailureReason`, `previewFailureCategory`, `previewLastUpdated`, `attempts`, `nextAttemptAt`, and `message`.

## Problem

`DocumentPreview` already stored queue responses, but it only partially consumed them:

- `previewStatusOverride` and `previewFailureOverride` drove most of the resolved preview state
- `previewFailureCategory` was not treated as queue-response data
- `previewLastUpdated` was not surfaced in the preview status area
- the queue response was not merged through a single helper, so the resolution path was split across the component

## Design

Add a small local helper, `resolveDocumentPreviewQueueState(...)`, that merges:

1. the explicit queue mutation response from `nodeService.queuePreview(...)`
2. the existing fallback preview state from rendition summary, node details, and server preview

Merge rule:

- queue response fields win when present
- fallback preview state fills any missing queue response fields
- the resolved preview status, failure category, failure reason, and updated timestamp all come from the same merged state
- queue attempts and retry timing remain displayed from the queue response

## Files

- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
- `ecm-frontend/src/components/preview/documentPreviewQueueState.ts`
- `ecm-frontend/src/components/preview/documentPreviewQueueState.test.ts`

## Result

- `DocumentPreview` now treats queue response as the immediate source of truth after `queuePreview(...)`
- sparse queue responses still render with rich effective preview metadata
- the preview status area now shows the queue-updated timestamp when available
