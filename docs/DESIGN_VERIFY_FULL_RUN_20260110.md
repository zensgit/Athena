# Design: Verify Script Full Run (2026-01-10)

## Goal
- Validate end-to-end verification flow after mock maker configuration change.

## Approach
- Run `scripts/verify.sh` with restart enabled.
- Capture verification summary and log artifacts from `tmp/`.

## Files
- scripts/verify.sh
- tmp/*_verify-report.md
