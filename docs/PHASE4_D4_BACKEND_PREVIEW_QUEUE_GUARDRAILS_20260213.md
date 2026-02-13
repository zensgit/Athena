# Phase 4 - Day 4: Backend Preview Queue Guardrails + Queue Status Message (2026-02-13)

## Goal

1. Add server-side guardrails so `POST /api/v1/documents/{id}/preview/queue` does not wastefully retry **PERMANENT** preview failures unless `force=true`.
2. Make the queue endpoint self-explanatory by returning a short `message` (aligned with OCR queue semantics).
3. Keep UI consistent: when an action is hidden in the frontend, the backend should still be safe-by-default.

## Background

The backend already classifies preview failures via:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewFailureClassifier.java`

Categories:

- `UNSUPPORTED`: known unsupported types/reasons (including generic MIME types like `application/octet-stream`).
- `TEMPORARY`: transient infra/service errors (timeouts, 5xx, connection reset/refused).
- `PERMANENT`: everything else (for example malformed PDFs / parse errors).

Day 3 implemented UI gating so PERMANENT/UNSUPPORTED failures do not show retry actions. Day 4 adds server-side enforcement so the API is robust for future UIs and direct API callers.

## Changes

### Backend: Guardrails + Message Field

Updated:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

Key behaviors (when `force=false`):

- `READY` -> `queued=false`, `message="Preview already up to date"`
- `UNSUPPORTED` -> `queued=false`, `message="Preview unsupported"`
- `FAILED` + classifier `UNSUPPORTED` -> `queued=false`, `message="Preview unsupported"`
- `FAILED` + classifier `PERMANENT` -> `queued=false`, `message="Preview failed permanently; use force=true to rebuild"`
- Existing queued job -> `queued=true`, `message="Preview already queued"`
- Newly queued job -> `queued=true`, `message="Preview queued"`

Also:

- `PreviewQueueStatus` now includes a `message` field (aligned with `OcrQueueStatus`).
- Redis backend path returns the same message semantics.

Tests updated/added:

- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
  - Verifies unsupported skip message.
  - Verifies PERMANENT failures are blocked without force.
  - Verifies TEMPORARY failures are allowed without force and transition to PROCESSING.

### Frontend: Use `message` + Fix Upload Force Bug

Updated queue toasts to prefer the server-provided `message`:

- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`

Type updated:

- `ecm-frontend/src/services/nodeService.ts` (`PreviewQueueStatus.message`)

Bug fix:

- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`
  - Previously posted `{ force }` in the request body; backend expects query param `force`.
  - Switched to `nodeService.queuePreview(nodeId, force)` so Force Rebuild actually forces.

## Verification

### Backend Tests

```bash
cd ecm-core
mvn -q test
```

Result: **pass**.

### Frontend Unit Tests

```bash
cd ecm-frontend
CI=true npm test -- --watchAll=false
```

Result: **pass** (13 suites / 70 tests).

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

### Runtime Note (E2E Target)

Playwright runs against the Docker-served UI at `http://localhost:5500`. After code changes, rebuild/recreate the containers (for example):

```bash
bash scripts/restart-ecm.sh
```

## Follow-Ups

- Day 2 (planned): MIME normalization/sniffing for `application/octet-stream` uploads (reduces false UNSUPPORTED).
- Optional: consider returning `previewStatus=PROCESSING` in the queue response when a job is actually queued (currently the UI treats queued status as PROCESSING client-side).

