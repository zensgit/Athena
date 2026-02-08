# Phase 1 P59 - Preview Unsupported Taxonomy (Design) (2026-02-08)

## Summary

Add a distinct preview status `UNSUPPORTED` (separate from `FAILED`) so the UI can:

- Show a neutral "Preview unsupported" state for non-previewable content types (for example `application/octet-stream`).
- Hide retry actions for unsupported previews (retries are only meaningful for transient failures).
- Provide accurate per-page filtering/counts in Search Results and Advanced Search.

This change includes backend status taxonomy + a data normalization migration, and frontend normalization to handle stale search index data.

## Goals

- Differentiate **retryable preview failures** from **non-retryable unsupported previews**.
- Make search UI preview status filters meaningful (`FAILED` vs `UNSUPPORTED`).
- Avoid repeated retry attempts for unsupported types.
- Preserve backwards compatibility for existing data / stale search index values.

## Non-Goals

- Add server-side (Elasticsearch) filtering by preview status across the full result set.
- Implement a full reindex operation as part of this change.

## Problem Statement

Historically, unsupported preview generation (example: `application/octet-stream`) was stored/displayed as a generic `FAILED` state. That caused:

- UI confusingly showing "Preview failed" for content that will never be previewable.
- Retry actions being offered for unsupported documents, leading to repeated no-op jobs and noise.
- Search facet/count ambiguity: `FAILED` included both retryable and non-retryable cases.

## Design Overview

### Backend taxonomy

Introduce a new `PreviewStatus.UNSUPPORTED`.

- `FAILED`: preview generation attempted and failed due to transient/permanent errors (retry may help for transient).
- `UNSUPPORTED`: preview generation is not available for this content type/reason (retry should not be offered).

### Classification source of truth

`PreviewResult.supported=false` is used both for unsupported types and runtime errors, so status cannot be derived from `supported` alone.

Instead, we classify the failure using the same `PreviewFailureClassifier` logic and then decide:

- If `failureCategory == UNSUPPORTED` => `PreviewStatus.UNSUPPORTED`
- Else => `PreviewStatus.FAILED`

### Data normalization (legacy rows)

Existing rows that are clearly "unsupported" (based on `preview_failure_reason`) are normalized from `FAILED` to `UNSUPPORTED` via Liquibase.

Note: Elasticsearch may still contain the previous `previewStatus` until those documents are reindexed. The frontend therefore normalizes to an **effective status** when needed.

### Frontend effective status normalization

To ensure UI remains correct even when search index results are stale:

- If backend returns `UNSUPPORTED`: treat as `UNSUPPORTED`.
- If backend returns `FAILED` but failure metadata indicates unsupported (category/reason/mime heuristics): treat as `UNSUPPORTED`.

This effective status is used for:

- Per-page preview status counts.
- Preview status chip filtering.
- Preview status chip rendering (label + retry actions).

## Implementation Details

### Backend changes

- `ecm-core/src/main/java/com/ecm/core/entity/PreviewStatus.java`
  - Add enum value: `UNSUPPORTED`.

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewFailureClassifier.java`
  - Treat `previewStatus=UNSUPPORTED` as a failure status.
  - If status is `UNSUPPORTED`, return category `UNSUPPORTED` immediately.
  - Preserve existing heuristics for mime/reason classification.

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
  - When `result.supported=false`, compute `failureCategory` via `PreviewFailureClassifier`.
  - Set persisted status:
    - `UNSUPPORTED` if category is `UNSUPPORTED`
    - else `FAILED`
  - Persist `previewFailureReason`, set `previewAvailable=false`, and attempt to update the search index.

### Data migration

- `ecm-core/src/main/resources/db/changelog/changes/029-normalize-preview-unsupported-status.xml`
  - Updates legacy `documents` rows from `FAILED` -> `UNSUPPORTED` based on `preview_failure_reason` patterns such as:
    - `preview not supported...`
    - `...not supported for mime type...`
    - `...unsupported media type...`
    - `...unsupported format/file type...`
  - Also sets `preview_available=false` and `preview_last_updated=NOW()`.

- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`
  - Includes change `029-normalize-preview-unsupported-status.xml`.

### Frontend changes

- `ecm-frontend/src/utils/previewStatusUtils.ts`
  - Add `getEffectivePreviewStatus(...)` and update `summarizeFailedPreviews(...)` to count:
    - `FAILED` as retryable issues
    - `UNSUPPORTED` as non-retryable issues
  - Keep existing unsupported detection logic (category/reason/mime).

- `ecm-frontend/src/pages/SearchResults.tsx`
  - Use `getEffectivePreviewStatus` for:
    - Preview status chip rendering
    - Preview status counts and filtering
  - Add `UNSUPPORTED` to the Preview Status filter chip list.
  - Show "Preview issues" summary (retryable vs unsupported) and hide retry actions when all issues are unsupported.

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - Add `UNSUPPORTED` to Preview Status filter chips + URL parsing.
  - Counts/filters use `getEffectivePreviewStatus`.

### E2E guardrails

- `ecm-frontend/e2e/search-preview-status.spec.ts`
  - Asserts `GET /api/v1/documents/:id/preview` returns:
    - `supported=false`
    - `failureCategory=UNSUPPORTED`
    - `status=UNSUPPORTED`
  - Confirms UI hides retry actions for unsupported previews.
  - Confirms Advanced Search can apply `previewStatus=UNSUPPORTED` via URL and keeps it on reload.

## Public Interface / Compatibility Notes

- API responses containing `previewStatus` may now return `UNSUPPORTED` (in addition to existing values).
  - Frontend uses `string` type, so this is non-breaking locally.
  - External consumers must tolerate/handle the new value if they parse it as a strict enum.

- Database values in `documents.preview_status` may now contain `UNSUPPORTED`.

## Operational Notes

- Elasticsearch `previewStatus` may lag after DB normalization until reindex occurs.
  - Frontend effective status mapping is intended to mask this temporarily.

## Future Improvements (Not in this change)

- Add server-side preview status filtering (so filters apply across all pages, not only current page).
- Add a maintenance job or endpoint to reindex documents after `preview_status` normalization.

