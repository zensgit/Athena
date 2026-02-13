# Phase 3 Day 1 — OCR (ML Service + Core Queue) Design (2026-02-12)

## Problem

For scanned PDFs and images, Apache Tika frequently produces **empty text**. That breaks:
- full-text search relevance (no searchable content)
- auto-matching (correspondent assignment)
- ML classification/tag suggestions

Paperless-ngx demonstrates that OCR is the practical baseline for “inbox → searchable docs”.

## Goals (Day 1 slice)

1. Provide a **server-side OCR API** in `ml-service` (Tesseract-backed) with conservative limits.
2. Add a **core-side OCR queue** that runs asynchronously (scheduled polling) and:
   - updates `Document.textContent`
   - writes OCR status/failure info to `Document.metadata`
   - reindexes the document in Elasticsearch
3. Expose a manual queue endpoint for on-demand OCR:
   - `POST /api/v1/documents/{id}/ocr/queue?force=false`

## Non-goals (Day 1 slice)

- UI surfacing (status chips, retry buttons) will be Day 3.
- Persistent queue (Redis/RabbitMQ) will be Day 5.
- Full enrichment re-run (auto-match + ML suggestions) will be Day 4.

## Architecture

### ML Service

- New endpoint: `POST /api/ml/ocr`
- Implementation:
  - `pytesseract` + Tesseract binary for OCR
  - `pdf2image` + `poppler-utils` for PDF → images
- Safety limits:
  - max upload bytes (`OCR_MAX_UPLOAD_BYTES`, default 20MB)
  - max pages (`OCR_MAX_PAGES`, default 3, clamp 1..20)
  - max chars (`OCR_MAX_CHARS`, default 200k, clamp 1k..2M)

Request:
- `multipart/form-data` with `file`
- query params:
  - `language` (default `OCR_DEFAULT_LANG`, default `eng`)
  - `maxPages`
  - `maxChars`

Response (JSON):
- `text`: extracted text (possibly truncated)
- `pages`: pages processed (PDF) or `1` (image)
- `language`: used OCR language
- `truncated`: boolean

### ECM Core

- New service: `com.ecm.core.ocr.OcrQueueService`
  - in-memory queue + de-dup by `documentId`
  - retry (bounded)
  - scheduled polling via `@Scheduled`
  - marks status into `Document.metadata`

Metadata keys written:
- `ocrStatus`: `PROCESSING | READY | FAILED | SKIPPED`
- `ocrFailureReason`: string (when relevant)
- `ocrLastUpdated`: ISO-8601 string
- `ocrProvider`: `ml-service`
- `ocrLanguage`: tesseract language string
- `ocrPages`: integer
- `ocrTruncated`: boolean
- `ocrChars`: integer

New API:
- `POST /api/v1/documents/{documentId}/ocr/queue?force=false`
  - returns queue status (queued/attempts/nextAttemptAt)

Event hook:
- On version creation (`EcmEventListener.handleVersionCreated`), OCR is enqueued.
  - actual execution is still guarded by `ecm.ocr.enabled=false` by default.

## Configuration

Core flags (defaults are safe / off):
- `ecm.ocr.enabled=false`
- `ecm.ocr.language=eng` (you can set to `eng+chi_sim` once container language packs are present)
- `ecm.ocr.max-pages=3`
- `ecm.ocr.max-bytes=20000000`
- `ecm.ocr.max-chars=200000`

Queue tuning:
- `ecm.ocr.queue.enabled=true`
- `ecm.ocr.queue.batch-size=1`
- `ecm.ocr.queue.max-attempts=2`
- `ecm.ocr.queue.retry-delay-ms=60000`
- `ecm.ocr.queue.poll-interval-ms=5000`

## Failure Modes

- OCR dependencies missing in ml-service build:
  - endpoint returns `503` with a clear message
- Unsupported mime type:
  - core marks `SKIPPED`
- Input too large:
  - core marks `FAILED` (bounded retries)
- ML service down:
  - core marks `FAILED` (bounded retries)

## Security / Privacy

- No OCR content is logged.
- No secrets are introduced. All credentials remain in env vars / gitignored `.env*`.
- Conservative size/page limits reduce resource exhaustion risk.

