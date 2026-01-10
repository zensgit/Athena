# Design: verify.sh Latest Summary (2026-01-10)

## Goal
- Capture the latest verify summary after the full run.

## Approach
- Execute `scripts/verify.sh --report-latest` to produce the aggregated report.

## Files
- scripts/verify.sh
- tmp/verify-latest.json
- tmp/verify-latest.md
