# Phase 3 (Day 2) — OCR End-to-End Smoke (API) Verification — 2026-02-12

## Prereqs

- Docker is running
- `ml-service` is up and healthy
- `ecm-core` is up and healthy
- OCR enabled in core:
  - add to `.env` (gitignored): `ECM_OCR_ENABLED=true`

## Steps

1. Restart core services (rebuilds `ml-service`, `ecm-core`, `ecm-frontend`):

   ```bash
   ./scripts/restart-ecm.sh
   ```

2. Obtain an admin token (writes to `tmp/admin.access_token`):

   ```bash
   ./scripts/get-token.sh admin admin
   ```

3. Run OCR smoke:

   ```bash
   ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token ./scripts/verify-ocr.sh
   ```

## Expected Result

- Script prints:
  - `queued=true`
  - `ocr_status=READY`
  - `search=OK`
  - `OK`
- Exit code is `0`.

## Failure Triage

- `ERROR: OCR was not queued ... message=OCR disabled`
  - Ensure `.env` includes `ECM_OCR_ENABLED=true`, then rerun `./scripts/restart-ecm.sh`.

- `ERROR: OCR failed ...`
  - Check `ml-service` logs for dependency/runtime issues (Tesseract/Poppler).
  - Check `ecm-core` logs for ML service connectivity errors.

- `ERROR: Search did not return the OCR token ...`
  - Elasticsearch may be slow to refresh; rerun with a longer wait:
    - `ECM_OCR_SEARCH_WAIT_SECONDS=180 ... ./scripts/verify-ocr.sh`

