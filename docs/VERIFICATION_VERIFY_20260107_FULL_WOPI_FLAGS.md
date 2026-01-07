# Athena ECM Verification Report (2026-01-07)

## Scope
- `scripts/verify.sh --no-restart --wopi-cleanup --wopi-query=<query>` full verification run.

## Environment
- Frontend: http://localhost:5500
- Backend: http://localhost:7700
- Keycloak: http://localhost:8180

## Command
- `bash scripts/verify.sh --no-restart --wopi-cleanup --wopi-query=__wopi_full_<epoch>`

## Results
- PASS (Passed 10, Failed 0, Skipped 1, Exit code 0).

## Artifacts
- Report: `tmp/20260107_094935_verify-report.md`
- WOPI summary: `tmp/20260107_094935_verify-wopi.summary.log`
- Logs prefix: `tmp/20260107_094935_*`
