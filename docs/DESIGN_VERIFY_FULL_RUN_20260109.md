# Design: Verify Script Full Run (2026-01-09)

## Goal
- Re-run the full verification script to validate restart, smoke, WOPI, and E2E flows end-to-end.

## Approach
- Execute `scripts/verify.sh` with service restart enabled.
- Capture the summary and log artifacts produced in `tmp/`.

## Files
- scripts/verify.sh
- tmp/*_verify-report.md
