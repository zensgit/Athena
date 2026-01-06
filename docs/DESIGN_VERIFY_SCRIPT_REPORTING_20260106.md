# Design: verify.sh Reporting (2026-01-06)

## Goal
- Provide a lightweight summary for verify.sh runs, including WOPI details.

## Approach
- Capture the original CLI arguments and always emit a report on exit.
- Write `tmp/<timestamp>_verify-report.md` with pass/fail counts and artifact paths.
- When the WOPI step runs, write `tmp/<timestamp>_verify-wopi.summary.log` with `[verify]` output lines.

## Files
- scripts/verify.sh
