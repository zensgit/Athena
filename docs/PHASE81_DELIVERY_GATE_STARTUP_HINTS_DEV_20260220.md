# Phase 81: Delivery Gate Startup Diagnostics Hints

## Date
2026-02-20

## Background
- Delivery gate already reported failed stages and first failure lines.
- Startup-related failures needed a dedicated hint section for faster triage.

## Goal
1. Add startup-focused diagnostics hints on gate failure.
2. Cover common startup failure categories:
   - stale static/prebuilt target
   - storage restriction symptoms
   - auth bootstrap timeout symptoms

## Changes

### 1) Startup hint aggregator in delivery gate
- File: `scripts/phase5-phase6-delivery-gate.sh`
- Added `print_startup_failure_hints`:
  - scans failed stage logs for startup symptom patterns
  - prints a dedicated `startup diagnostics hints` section on failure
- Hint categories:
  - static/prebuilt target staleness patterns
  - storage API restriction (`sessionStorage` / `localStorage` / `SecurityError`)
  - auth timeout patterns (`Auth initialization timed out`, `Request timed out`, `ECONNABORTED`)

### 2) Failure path integration
- On non-zero gate exit, startup hints are printed before final failure summary.

## Non-Functional Notes
- No changes to PASS path behavior.
- No change to stage execution order.
