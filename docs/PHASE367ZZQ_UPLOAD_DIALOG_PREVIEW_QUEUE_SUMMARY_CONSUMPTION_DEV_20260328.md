# Phase367ZZQ - Upload Dialog Preview Queue Summary Consumption

## Goal

Extend the new preview-queue effective summary contract to the upload work surface so newly uploaded documents reflect queue-triggered preview semantics immediately.

## Why

Phase367ZZP made queue responses rendition-aware and wired them into ordinary search and advanced search.

`UploadDialog` still only copied a single `previewStatus` string after queueing, which meant:

- failure reason/category could remain stale
- unsupported/failed detail still depended on the next refresh cycle

## Design

File:

- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`

When `queuePreview(...)` returns:

- continue to promote `queued -> PROCESSING` for immediate visual feedback
- also persist:
  - `previewFailureReason`
  - `previewFailureCategory`

onto the uploaded item row

This keeps the existing auto-refresh behavior, but removes the unnecessary gap before the next refresh lands.

## Result

After this phase, upload rows use the same richer queue-summary contract as the search surfaces and no longer lose failure-detail updates between queueing and refresh.
