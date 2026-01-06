# Athena ECM Verification Report (2026-01-06)

## Scope
- `scripts/verify.sh` full verification (restart, health checks, tokens, smoke, Phase C, frontend build, WOPI).
- WOPI-only rerun with an indexed XLSX sample and an explicit search query.

## Environment
- Frontend: http://localhost:5500
- Backend: http://localhost:7700
- Keycloak: http://localhost:8180

## Commands
- `bash scripts/verify.sh`
- `ECM_VERIFY_QUERY=verify-wopi bash scripts/verify.sh --wopi-only`
- `bash scripts/verify.sh --no-restart`

## Results
- Full run: FAILED at WOPI step (no `.xlsx` document found).
- WOPI-only rerun: PASS (Passed 3, Failed 0, Skipped 7).
- Full run (no restart): PASS (Passed 10, Failed 0, Skipped 1).

## Notes
- Uploaded `verify-wopi-sample.xlsx` under `/Root/Documents`, indexed via `/api/v1/search/index/{id}`.
- WOPI verification used `ECM_VERIFY_QUERY=verify-wopi` to find the sample.
- E2E tests were skipped in the WOPI-only rerun.

## Artifacts
- Logs prefix (full run): `tmp/20260106_221152_*`
- Logs prefix (WOPI-only rerun): `tmp/20260106_223401_*`
- Logs prefix (full run, no restart): `tmp/20260106_225121_*`
