# Verification: verify.sh Report Latest (2026-01-08)

- Command: `bash scripts/verify.sh --report-latest=3` (also supports `--report-latest 3`)
- Result: latest summary written in JSON + Markdown with aggregate totals, WOPI status counts, and top step durations.
- JSON summary: `tmp/verify-latest.json`
- Markdown summary: `tmp/verify-latest.md`
- Step stats CSV: `tmp/verify-latest-steps.csv`
- Runs CSV: `tmp/verify-latest-runs.csv`

## Status filtering
- Command: `bash scripts/verify.sh --report-latest-status=PASSED --report-latest=2`
- Result: summary limited to PASSED runs and filter recorded in the output. Invalid status returns a non-zero exit code.

## Since filtering
- Command: `bash scripts/verify.sh --report-latest-since=20260108_000000 --report-latest=2`
- Result: summary limited to runs at/after the timestamp and filter recorded in JSON + Markdown output.
- Command: `bash scripts/verify.sh --report-latest-since=bad --report-latest=1`
- Result: exits non-zero with `ERROR: --report-latest-since must match YYYYMMDD_HHMMSS`.

## Combined filters
- Command: `bash scripts/verify.sh --report-latest-since=20260108_000000 --report-latest-status=PASSED --report-latest=2`
- Result: summary limited to PASSED runs at/after the timestamp and both filters recorded in JSON + Markdown output.
