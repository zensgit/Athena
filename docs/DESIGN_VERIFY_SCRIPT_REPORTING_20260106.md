# Design: verify.sh Reporting (2026-01-06)

## Goal
- Provide a lightweight summary for verify.sh runs, including WOPI details.

## Approach
- Capture the original CLI arguments and always emit a report on exit.
- Write `tmp/<timestamp>_verify-report.md` with pass/fail counts, exit code, and artifact paths.
- Include the WOPI status in the report when a WOPI summary exists.
- Include the WOPI skip reason in the report when present.
- When the WOPI step runs, write `tmp/<timestamp>_verify-wopi.summary.log` with `[verify]` output lines.
- When WOPI is skipped, write a summary with `status: skipped` and the skip reason.
- Write a step summary log (`tmp/<timestamp>_verify-steps.log`) with status and duration per step.
- Include skip reasons in the step summary CSV.
- Record total duration in the verification report.
- Write a JSON summary (`tmp/<timestamp>_verify-summary.json`) with results and artifact paths.
- Print report + WOPI summary paths to the console on exit.
- Emit a report even when CLI parsing fails, capturing the non-zero exit code.

## Files
- scripts/verify.sh
