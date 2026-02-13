# Phase 3 Day 3: OCR UI Status + Queue Actions (Design) — 2026-02-12

## Goal

Make OCR visible and operable from the UI so operators can:
- See whether OCR is running / ready / failed for a document.
- Re-queue OCR (and force re-run) without leaving the preview flow.

This is intentionally minimal UI surfacing (status + actions) to keep scope tight and verification reliable.

## User-Facing Behavior

In the document preview dialog (`DocumentPreview`):

1. **OCR status chip** is shown when OCR is known:
   - `OCR: Processing`, `OCR: Ready`, `OCR: Failed`, `OCR: Skipped`
   - If OCR is disabled or the document is not eligible, the chip shows:
     - `OCR: Disabled` / `OCR: Not eligible` / `OCR: Unavailable`

2. **Retry actions** are available in the “More actions” menu:
   - `Queue OCR`
   - `Force OCR Rebuild`

3. While OCR is running, the dialog shows an info banner:
   - “OCR extraction is in progress. Status updates every 15s.”

4. When OCR fails, the dialog shows a warning banner with action buttons:
   - `Retry OCR`
   - `Force OCR`

## Backend Contract Used

Endpoint:
- `POST /api/v1/documents/{documentId}/ocr/queue?force={true|false}`

Response (from `OcrQueueService.OcrQueueStatus`):
- `documentId: string`
- `ocrStatus: string | null` (e.g. `PROCESSING`, `READY`, `FAILED`, `SKIPPED`)
- `queued: boolean`
- `attempts: number`
- `nextAttemptAt: string | null`
- `message: string | null` (e.g. `OCR disabled`, `Not eligible for OCR`)

OCR status and failure reason are stored in node metadata (read via node details):
- `metadata.ocrStatus`
- `metadata.ocrFailureReason`

## Frontend Implementation

### API Wrapper

Add `queueOcr(nodeId, force)` to the node service layer:
- `ecm-frontend/src/services/nodeService.ts`

### Preview Dialog UI

File:
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`

Key behavior:
- Fetch node details (`GET /api/v1/nodes/{id}`) on open to obtain `metadata.ocrStatus`.
- Poll node details every 15 seconds while `ocrStatus === PROCESSING`.
- Display OCR status chip alongside the existing preview status chip.
- Add menu items for OCR queue actions, calling `nodeService.queueOcr(...)`.
- Show banners when OCR is processing or failed (similar to preview queue UX).

Design choices:
- OCR fetching/polling is **best-effort** and must not block preview rendering.
- UI uses lightweight heuristics on `message` when OCR is disabled/not-eligible to provide a stable operator cue.

## Testing & Verification Plan

Automated UI verification is added via Playwright:
- `ecm-frontend/e2e/ocr-queue-ui.spec.ts`

Coverage:
- Open preview for a newly uploaded PDF.
- Verify menu shows `Queue OCR` and `Force OCR Rebuild`.
- Click each action and assert the request is issued and OCR chip becomes visible.

Notes:
- The test is resilient even when OCR is disabled: the backend returns `message: OCR disabled` and the UI shows `OCR: Disabled`.

