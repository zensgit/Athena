# Phase 131: Gate Plan Mode and Help CLI

## Date
2026-02-25

## Background
- Gate now supports multiple execution modes and registry controls.
- Operators needed a clearer way to:
  - discover supported modes/flags
  - preview stage execution before running expensive checks.

## Goals
1. Add first-class CLI help output.
2. Add plan-only mode for dry planning without execution.
3. Print execution plan by default for better observability.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - added CLI parsing for:
    - `--help` / `-h`
    - `--mode=<mode>`
    - positional mode argument (`all|mocked|integration|preflight|integration-preflight`)
    - `--plan` (plan-only; no stage execution)
  - added env:
    - `DELIVERY_GATE_PRINT_EXECUTION_PLAN` (default `1`)
  - new outputs:
    - usage/help section
    - `execution plan` section with resolved fast/integration behavior
    - `plan-only mode complete` terminal line when using `--plan`

## Impact
- No runtime product behavior change.
- Better ergonomics and safer operator workflows for local/CI gate invocations.
