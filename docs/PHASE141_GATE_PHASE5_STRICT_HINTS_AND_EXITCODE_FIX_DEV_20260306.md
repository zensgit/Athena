# Phase 141: Delivery Gate Phase5 Strict Hints and Exit-Code Propagation Fix

## Date
2026-03-06

## Background
- After adding mocked summary artifact integration, mocked stage could print strict guard failure lines but still return success in gate due function return-code masking.
- Gate failure hints did not yet consume strict failure details from phase5 summary payload.

## Goals
1. Ensure mocked regression stage propagates `phase5-regression` non-zero exit code.
2. Extract strict failure details from summary artifact and print actionable hints in gate failure diagnostics.
3. Keep successful mocked/default flow unchanged.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - fixed `run_mocked_regression_stage`:
    - capture `phase5-regression` return code in `phase5_rc`
    - still print summary artifact generated/missing lines
    - return `phase5_rc` explicitly (prevents accidental PASS on strict failures)
  - added summary parsing helpers:
    - `extract_phase5_summary_path_from_log`
    - `extract_phase5_summary_strict_kv`
  - enhanced `print_startup_failure_hints`:
    - parse strict failure reasons from summary JSON
    - print strict hotspot/flaky threshold hint lines with threshold+match count
    - print normalized strict reason list.

## Compatibility
- Default mocked flow with non-strict settings remains PASS.
- Strict-failure mode now correctly returns non-zero at gate level and emits explicit remediation hints.
