# Athena ECM Verification Report (2026-01-08)

## Scope
- `scripts/verify.sh` full verification (restart, health checks, tokens, smoke, Phase C, frontend build, WOPI, E2E).

## Environment
- Frontend: http://localhost:5500
- Backend: http://localhost:7700
- Keycloak: http://localhost:8180

## Command
- `bash scripts/verify.sh`

## Results
- FAIL (Passed 10, Failed 1, Skipped 0, Exit code 1).
- E2E failure: `ecm-frontend/e2e/ui-smoke.spec.ts:402` (timeout waiting for correspondent search row).
- Playwright summary: 16 passed, 1 failed.

## Follow-up
- Isolated UI smoke rerun after correspondent list stabilization: `docs/VERIFICATION_E2E_CORRESPONDENT_LIST_20260108.md`.

## Artifacts
- Report: `tmp/20260108_132606_verify-report.md`
- WOPI summary: `tmp/20260108_132606_verify-wopi.summary.log`
- Step summary: `tmp/20260108_132606_verify-steps.log`
- E2E log: `tmp/20260108_132606_e2e-test.log`
- Playwright artifacts: `ecm-frontend/test-results/ui-smoke-UI-smoke-browse-u-0d494-py-move-facets-delete-rules-chromium/`
- Logs prefix: `tmp/20260108_132606_*`
