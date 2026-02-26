# Phase 132: Gate Plan Format and No-Plan Flags

## Date
2026-02-26

## Background
- Gate already supported usage/help and plan-only flow (`--plan`).
- Operators still needed two controls:
  - structured execution-plan output for automation tooling
  - an explicit way to disable plan printing for clean logs.

## Goals
1. Add machine-readable execution plan output.
2. Add explicit plan print toggles in CLI.
3. Keep gate execution behavior unchanged for existing defaults.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - new env:
    - `DELIVERY_GATE_EXECUTION_PLAN_FORMAT` (default: `text`, supports `text|json`)
  - new CLI flags:
    - `--no-plan`
    - `--print-plan`
    - `--plan-format=<text|json>`
    - `--plan-json`
    - `--plan-text`
  - `print_execution_plan` enhanced:
    - internally resolves layer plans once
    - emits JSON when `DELIVERY_GATE_EXECUTION_PLAN_FORMAT=json`
    - preserves text output for default path
  - startup diagnostics now print:
    - `DELIVERY_GATE_EXECUTION_PLAN_FORMAT=...`
  - format guard:
    - invalid plan format now fails fast with explicit error.

## Compatibility
- Default behavior remains unchanged:
  - plan still prints by default (`DELIVERY_GATE_PRINT_EXECUTION_PLAN=1`)
  - text output remains the default format.
- Existing `--plan` semantics stay intact (plan-only + early exit).

## Impact
- Better operator ergonomics for both humans (`text`) and automation (`json`).
- Lower risk of noisy logs in CI/local runs via `--no-plan`.
