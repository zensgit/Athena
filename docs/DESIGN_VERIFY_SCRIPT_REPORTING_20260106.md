# Design: verify.sh Reporting (2026-01-06)

## Goal
- Provide a lightweight summary for verify.sh runs, including WOPI details.

## Approach
- Capture the original CLI arguments and always emit a report on exit.
- Write `tmp/<timestamp>_verify-report.md` with pass/fail counts, exit code, and artifact paths.
- When the WOPI step runs, write `tmp/<timestamp>_verify-wopi.summary.log` with `[verify]` output lines.
- Print report + WOPI summary paths to the console on exit.
- Emit a report even when CLI parsing fails, capturing the non-zero exit code.

## Files
- scripts/verify.sh
