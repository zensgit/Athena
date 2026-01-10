# Design: verify.sh Latest Summary (2026-01-09)

## Goal
- Capture a quick summary of recent verify runs for reporting.

## Approach
- Execute `scripts/verify.sh --report-latest` to generate aggregated JSON/Markdown outputs.

## Files
- scripts/verify.sh
- tmp/verify-latest.json
- tmp/verify-latest.md
