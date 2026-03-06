# Phase 138: Delivery Gate Phase5 Summary Directory Integration

## Date
2026-03-06

## Background
- `phase5-regression` now supports `PHASE5_REGRESSION_SUMMARY_JSON`.
- Delivery gate still lacked a first-class way to collect these summary artifacts when running mocked layer orchestration.

## Goals
1. Let delivery gate configure mocked phase5 summary artifact output directory.
2. Keep default behavior unchanged when no directory is configured.
3. Surface summary target/generated path in gate logs for operator and CI traceability.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - added env:
    - `DELIVERY_GATE_PHASE5_SUMMARY_DIR`
  - added CLI:
    - `--phase5-summary-dir=<path>`
  - execution plan payload now includes mocked summary directory field.
  - mocked regression stage now:
    - builds `phase5-regression-summary-<utc-ts>.json` under configured directory
    - forwards path to `PHASE5_REGRESSION_SUMMARY_JSON`
    - prints `summary target` and `summary generated` lines
  - added guard:
    - fail fast when `DELIVERY_GATE_PHASE5_SUMMARY_DIR` points to a regular file.

## Compatibility
- When `DELIVERY_GATE_PHASE5_SUMMARY_DIR` is unset, behavior is unchanged.
- Existing mode flags and execution ordering remain unchanged.
