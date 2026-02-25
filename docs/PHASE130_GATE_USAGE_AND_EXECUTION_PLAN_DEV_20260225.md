# Phase 130: Gate Usage and Execution Plan UX

## Date
2026-02-25

## Background
- Delivery gate now has multiple modes and registry preflight controls.
- Operator ergonomics needed improvement for:
  - discovering available modes/flags
  - understanding which stages will run before execution.

## Goals
1. Add `--help` usage output with modes and key env controls.
2. Add execution-plan preview before gate execution.
3. Add `--plan` mode to print plan and exit without running stages.
4. Preserve existing gate behavior for normal executions.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - new CLI argument handling:
    - `--help` / `-h`
    - `--mode=<mode>`
    - positional mode (`all|mocked|integration|preflight|integration-preflight`)
    - `--plan` (plan-only)
  - new env:
    - `DELIVERY_GATE_PRINT_EXECUTION_PLAN` (default `1`)
  - startup now prints:
    - `DELIVERY_GATE_PRINT_EXECUTION_PLAN`
    - `DELIVERY_GATE_PLAN_ONLY`
  - new plan output section:
    - resolved mode
    - preflight executor path
    - fast/integration stage plan
  - `--plan` exits early with:
    - `phase5_phase6_delivery_gate: plan-only mode complete`

## Impact
- No runtime product behavior change.
- Reduces operator misconfiguration risk and shortens troubleshooting loop by making intended execution explicit.
