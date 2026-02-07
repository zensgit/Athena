# Phase 1 P54 - Preview Unsupported Status Normalization Design (2026-02-07)

## Background
- Search/advanced search/list/upload/preview UI currently shows `Preview failed` for generic binary mime types (for example `application/octet-stream`).
- Those files are often not preview-capable by design, so labeling as failure creates noisy false alarms and unnecessary retry attempts.

## Goal
- Normalize `FAILED + unsupported mime` to a neutral `Preview unsupported` presentation.
- Keep actual preview generation failures as `Preview failed`.

## Scope
- Frontend:
  - `ecm-frontend/src/utils/previewStatusUtils.ts`
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - `ecm-frontend/src/pages/SearchResults.tsx`
  - `ecm-frontend/src/components/browser/FileList.tsx`
  - `ecm-frontend/src/components/dialogs/UploadDialog.tsx`
  - `ecm-frontend/src/components/preview/DocumentPreview.tsx`
  - tests/docs

## Design Changes

### 1) Shared preview-status utility
- Added `previewStatusUtils`:
  - `normalizeMimeType`
  - `isUnsupportedPreviewMimeType`
  - `getFailedPreviewMeta`
- Unsupported mime allowlist:
  - `application/octet-stream`
  - `binary/octet-stream`
  - `application/x-empty`

### 2) UI label normalization
- For `previewStatus=FAILED`:
  - unsupported mime => `Preview unsupported` (neutral color)
  - other mime => `Preview failed` (error color)

### 3) Retry action guard
- In search/advanced-search result cards:
  - hide retry action for unsupported preview mime failures
  - keep retry action for true preview failures

### 4) Failure summary cleanup
- Failure reason summary in search/advanced search excludes unsupported-mime records to reduce false failure buckets.

## Compatibility
- No backend/API changes.
- Existing preview queue/retry behavior remains unchanged for real preview-capable failures.
