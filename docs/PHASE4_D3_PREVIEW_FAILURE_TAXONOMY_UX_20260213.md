# Phase 4 - Day 3: Preview Failure Taxonomy + UX Messaging (2026-02-13)

## Goal

Make preview failures actionable and consistent across:

- Search Results (`/search-results`)
- Advanced Search (`/search`)
- Document Preview dialog
- Upload dialog (post-upload status list)

Specifically:

- `TEMPORARY` failures should show retry actions (per-item + bulk).
- `PERMANENT` failures should **not** show retry actions (retry is wasteful); show guidance instead.
- `UNSUPPORTED` failures should show neutral status and no retry actions.

## Background / Taxonomy

Backend categorizes preview failures via:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewFailureClassifier.java`

Categories:

- `UNSUPPORTED`: file type not supported, or known unsupported reasons, or generic MIME types (e.g. `application/octet-stream`).
- `TEMPORARY`: transient infrastructure/service errors (timeouts, 5xx, connection resets/refusals).
- `PERMANENT`: everything else (for example malformed PDFs / parse errors).

Frontend must not guess "retryability" from `previewStatus=FAILED` alone.

## UX Rules (Implemented)

For effective preview status:

1. `READY`
   - Show "Preview ready".
2. `PROCESSING` / `QUEUED`
   - Show progress status.
3. `UNSUPPORTED`
   - Show "Preview unsupported".
   - Hide retry actions.
4. `FAILED`
   - If retryable (`TEMPORARY` or transient-hint fallback): show retry + force rebuild.
   - If permanent: hide retry actions and show guidance:
     - Download the file and validate it.
     - Upload a corrected version.

Bulk actions:

- "Retry failed previews" and "Force rebuild failed previews" only target **retryable** failures (never permanent).

## Implementation Summary

### Preview Utility Layer

- `ecm-frontend/src/utils/previewStatusUtils.ts`
  - Added `isTemporaryPreviewReason()` and `isRetryablePreviewFailure()`.
  - `summarizeFailedPreviews()` now splits failures into:
    - `retryableFailed`
    - `permanentFailed`
    - `unsupportedFailed`
  - `getFailedPreviewMeta()` now labels:
    - `Preview failed (temporary)`
    - `Preview failed (permanent)`
    - `Preview unsupported`
- `ecm-frontend/src/utils/previewStatusUtils.test.ts`
  - Added/updated coverage for PERMANENT vs TEMPORARY behavior.

### UI Surfaces Updated

- `ecm-frontend/src/pages/SearchResults.tsx`
  - Per-item retry icons only show for `isRetryablePreviewFailure(...)`.
  - Bulk retry targets only retryable failures.
  - Preview issue summary includes "Permanent" count and message reflects permanent-only scenarios.
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - Same behavior as Search Results (per-item + bulk).
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
  - FAILED banner:
    - Retry actions only shown for retryable failures.
    - PERMANENT uses an error severity + guidance copy.
    - UNSUPPORTED uses info severity and hides actions.
- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`
  - Post-upload "Queue preview"/"Force rebuild" actions hidden for PERMANENT failures.

### E2E Updates

- `ecm-frontend/e2e/search-preview-status.spec.ts`
  - Updated the invalid PDF test to assert:
    - preview endpoint returns `failureCategory=PERMANENT`
    - UI hides per-item retry actions for that result
    - bulk "Retry failed previews" is hidden when there are no retryable items in scope

## Verification

### Frontend Unit Tests

```bash
cd ecm-frontend
CI=true npm test -- --watchAll=false
```

Result: **pass**.

### Playwright E2E Gate Subset

```bash
cd ecm-frontend
npx playwright test \
  e2e/ocr-queue-ui.spec.ts \
  e2e/pdf-preview.spec.ts \
  e2e/search-preview-status.spec.ts \
  --project=chromium
```

Result: **pass** (10 tests).

### Notes

- Playwright runs against the Docker-served UI at `http://localhost:5500`. After frontend changes, rebuild/recreate the `athena-ecm-frontend-1` container (for example via `bash scripts/restart-ecm.sh`) before running E2E.

