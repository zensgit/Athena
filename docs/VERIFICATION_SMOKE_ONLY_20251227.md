# Smoke Verification Report (2025-12-27)

## Scope
- Local smoke-only verification using `scripts/verify.sh --smoke-only`.
- Validate Keycloak readiness, API health, ClamAV health, tokens, smoke tests, frontend build, and WOPI preview flow.

## Result
- Status: PASS
- Summary: 10 passed, 0 failed, 1 skipped (E2E skipped by --smoke-only)

## Command
- `bash scripts/verify.sh --smoke-only`

## Key Logs
- Logs directory: `tmp/`
- Prefix: `20251227_155213_*`
- WOPI verification log: `tmp/20251227_155213_verify-wopi.log`

## Notes
- Updated WOPI verification to accept either "View Online" or "Edit Online" menu entry.
