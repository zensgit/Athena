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
- Include parsed step details in the JSON summary when the step log is available.
- Include wait and skip steps in the step summary with reasons where applicable.
- Support `--report-latest[=N]` to aggregate recent JSON summaries into `tmp/verify-latest.json` and `tmp/verify-latest.md`.
- Include aggregate totals (runs, pass/fail counts, duration stats) in report-latest outputs.
- Include WOPI status counts in report-latest aggregation.
- Support `--report-latest <N>` as a space-delimited alternative.
- Include top step duration summary in report-latest outputs.
- Support `--report-latest-status=PASSED|FAILED` to filter aggregation.
- Write `tmp/verify-latest-steps.csv` with aggregated step stats.
- Write `tmp/verify-latest-runs.csv` with per-run summary rows.
- Print report + WOPI summary paths to the console on exit.
- Emit a report even when CLI parsing fails, capturing the non-zero exit code.

## Files
- scripts/verify.sh
