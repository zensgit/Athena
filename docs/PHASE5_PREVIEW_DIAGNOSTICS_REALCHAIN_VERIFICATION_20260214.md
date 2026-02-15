# Phase 5 - Preview Diagnostics Real-Chain Verification (Optional, Docker Required)

Date: 2026-02-14

## Goal

Verify the end-to-end chain behind **Admin Preview Diagnostics**:

1. Upload a deliberately corrupted PDF
2. Preview pipeline attempts to generate a preview
3. Document becomes `FAILED` or `UNSUPPORTED` with a failure reason
4. The failure appears in:
   - `GET /api/v1/preview/diagnostics/failures`
   - UI page `/admin/preview-diagnostics`

## Prerequisites

- Full stack is running (API, DB, ES, preview pipeline workers, Keycloak)
- Admin credentials available (defaults to `admin/admin`)

## Command

```bash
bash scripts/phase5-preview-diagnostics-realchain.sh admin admin
```

## What It Does

The script `scripts/phase5-preview-diagnostics-realchain.sh`:

- Obtains an access token via `scripts/get-token.sh`
- Uploads a corrupted PDF to the Root folder
- Polls `/api/v1/preview/diagnostics/failures` until the uploaded filename appears
- Prints a JSON summary (id/status/category/reason/lastUpdated)

## Environment Overrides

- `ECM_API_URL` (default `http://localhost:7700`)
- `LIMIT` (default `200`)
- `POLL_SECONDS` (default `120`)
- `POLL_INTERVAL` (default `3`)
- `EXPECT_REASON_SUBSTR` (optional)

Example:

```bash
EXPECT_REASON_SUBSTR="Missing root object" bash scripts/phase5-preview-diagnostics-realchain.sh
```

## Expected Result

- Script prints `FOUND: {...}` and ends with `phase5_preview_realchain: ok`

If the document never appears:

- Script exits non-zero with a hint to ensure preview workers are running and the failure status is recorded.

## 2026-02-15 Post-Merge Hardening Update

### Why

During post-merge full-chain verification on `main`, upload of a deliberately corrupted PDF returned HTTP 400 while still creating a document (`documentId` present). This is expected in some pipelines where extraction errors are surfaced in the upload response.

Also, local temporary-file creation needed to be robust across environments where a stale template-named file may exist.

### Script Improvements

`scripts/phase5-preview-diagnostics-realchain.sh` was hardened to:

- Accept upload response HTTP 400 when `documentId` is present.
- Parse upload HTTP status and body explicitly instead of failing on `curl -f`.
- Use a safer `mktemp` flow (`/tmp/ecm-preview-corrupt.XXXXXX` then rename with `.pdf`) to avoid template collisions.

### Re-Verification (PASS, 2026-02-15)

Command:

```bash
bash scripts/phase5-preview-diagnostics-realchain.sh
```

Observed result:

- Uploaded corrupted PDF successfully resolved `documentId`.
- Failure appeared in diagnostics endpoint with:
  - `previewStatus=FAILED`
  - `previewFailureCategory=PERMANENT`
  - non-empty `previewFailureReason`
- Script ended with `phase5_preview_realchain: ok`.
