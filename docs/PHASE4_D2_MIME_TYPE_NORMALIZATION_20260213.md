# Phase 4 Day 2 - MIME Type Normalization (Octet-Stream) - 2026-02-13

## Goal

Reduce false `UNSUPPORTED` previews and incorrect viewer selection when uploads are mislabeled as `application/octet-stream`.

## Problem

Some upload paths can persist a generic MIME type (for example `application/octet-stream` / `application/x-empty`) even when the filename and/or content is a known format (PDF, images, etc.). This cascades into:

- Preview status facets treating the document as unsupported (see unsupported MIME signals).
- UI viewer selection treating the item as "unknown binary" and falling back to an unsupported preview state.

## Design / Approach

1. Prefer content sniffing first (most reliable).
2. Only when the result is generic, retry detection with filename metadata (Tika `RESOURCE_NAME_KEY`) to allow extension-based inference.

This keeps behavior safe by default while still fixing common “octet-stream but actually PDF” scenarios.

### Generic MIME types

We treat these as generic and eligible for filename fallback:

- `application/octet-stream`
- `binary/octet-stream`
- `application/x-empty`

## Implementation

### Backend

#### Filename-aware MIME detection with safe fallback

- `ecm-core/src/main/java/com/ecm/core/service/ContentService.java`
  - Added `detectMimeType(String contentId, String filename)`:
    - First pass: `tika.detect(stream)`
    - If generic: second pass `tika.detect(stream, metadata(filename))`
    - If still generic: keep original result

#### Apply to pipeline upload + versioning + legacy upload

- `ecm-core/src/main/java/com/ecm/core/pipeline/processor/ContentStorageProcessor.java`
  - Uses `contentService.detectMimeType(contentId, context.getOriginalFilename())` so pipeline uploads persist the normalized MIME type.
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
  - Uses filename-aware detection during check-in/version creation.
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
  - Legacy upload now sets the initial document MIME type from stored content detection (instead of trusting `MultipartFile#getContentType()`).

### Frontend / E2E

- `ecm-frontend/e2e/pdf-preview.spec.ts`
  - Added Playwright test:
    - Upload a PDF with multipart file `mimeType=application/octet-stream`
    - Assert node `contentType` normalizes to `application/pdf`
    - Assert the client PDF renderer shows `.react-pdf__Page__canvas`

## Verification

### Backend tests

```bash
cd ecm-core
mvn -q test
```

Result: PASS (2026-02-13)

### Playwright gate subset

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
  e2e/ocr-queue-ui.spec.ts \
  e2e/pdf-preview.spec.ts \
  e2e/search-preview-status.spec.ts \
  --project=chromium
```

Result: PASS (11 passed) (2026-02-13)

## Notes / Follow-ups

- If we want to hide "Retry failed previews" for PERMANENT failures entirely (not just block server-side), we should extend the UI bulk-action classification to incorporate PERMANENT vs TEMPORARY categories (Phase 4 Day 5/6).
