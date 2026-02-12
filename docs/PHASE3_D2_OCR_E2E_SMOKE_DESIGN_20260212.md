# Phase 3 (Day 2) — OCR End-to-End Smoke (API) Design — 2026-02-12

## Goal

Provide a **repeatable, API-level verification** that the OCR ingestion loop works end-to-end:

1. Upload a PDF
2. Queue OCR extraction (async)
3. Persist OCR status + extracted text into the document model
4. Update the search index so full-text search can find OCR text

This complements the Day 1 unit test by validating the integration between:
- `ecm-core` OCR queue
- `ml-service` OCR endpoint
- Search indexing pipeline

## Non-Goals

- UI/E2E coverage (handled in Day 3)
- Distributed job orchestration (Redis/RabbitMQ) (planned Day 5)

## Design Overview

### New Script

Add `scripts/verify-ocr.sh`:
- Uploads a minimal single-page PDF containing a unique token (e.g. `athena-ocr-<epoch>-<rand>`).
- Calls `POST /api/v1/documents/{id}/ocr/queue?force=true`.
- Polls `GET /api/v1/nodes/{id}` until `metadata.ocrStatus == READY` (or fails early on `FAILED`).
- Polls `GET /api/v1/search?q=<token>` until the uploaded document appears in results.

### Why a PDF, not an image

The existing system already supports PDFs as a primary document type and has stable upload paths.
The `ml-service` OCR endpoint uses `pdf2image` to rasterize the first N pages, so even text-PDFs are OCR-able.

### Feature Flags / Config

`ecm-core`:
- OCR is **disabled by default** to keep baseline behavior unchanged.
- Enable locally via `.env` (gitignored):
  - `ECM_OCR_ENABLED=true`

`ml-service`:
- OCR dependencies (Tesseract + Poppler + Python libs) are baked into the image via `ml-service/Dockerfile`.
- Defaults are conservative; optional env overrides:
  - `OCR_DEFAULT_LANG` (default `eng`)
  - `OCR_MAX_PAGES` (default `3`)
  - `OCR_MAX_CHARS` (default `200000`)
  - `OCR_MAX_UPLOAD_BYTES` (default `20000000`)

### Restart Workflow Improvement

Update `scripts/restart-ecm.sh` to also rebuild/recreate `ml-service` so OCR endpoint changes are not missed during local iterations.

## Public Interfaces

No new public APIs beyond Day 1.

This smoke test exercises:
- `POST /api/v1/documents/upload?folderId=<id>`
- `POST /api/v1/documents/{documentId}/ocr/queue?force=true`
- `GET /api/v1/nodes/{documentId}`
- `GET /api/v1/search?q=<token>`

## Acceptance Criteria

- With `ECM_OCR_ENABLED=true` and services running, `scripts/verify-ocr.sh` exits `0` and prints:
  - `queued=true`
  - `ocr_status=READY`
  - `search=OK`
- No credentials/tokens are written into repo-tracked files or logs.

