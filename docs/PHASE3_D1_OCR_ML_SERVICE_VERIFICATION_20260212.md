# Phase 3 Day 1 — OCR (ML Service + Core Queue) Verification (2026-02-12)

## Automated Verification

### 1) Backend Unit Test (fast)

Run from repo root:

```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace/ecm-core maven:3-eclipse-temurin-17 \
  mvn -q test -Dtest=OcrQueueServiceTest
```

Expected:
- test passes
- `OcrQueueService` updates `Document.textContent` and writes OCR metadata keys (`ocrStatus=READY` etc.)

## Manual Smoke (optional)

Prereqs:
- docker compose running with `ml-service` and `ecm-core`
- enable OCR in core via env var or profile config:
  - `ecm.ocr.enabled=true`

Steps:
1. Upload a scanned PDF or an image containing a known short phrase (e.g. `hello ocr`).
2. Queue OCR for the document:
   - `POST /api/v1/documents/{id}/ocr/queue`
3. Fetch node details until `metadata.ocrStatus` becomes `READY`.
4. Search for the phrase and confirm the document is returned.

Notes:
- OCR can take time for PDFs; keep PDFs small for local validation.
- If OCR dependencies are missing, the queue will transition to `FAILED` with a reason.

