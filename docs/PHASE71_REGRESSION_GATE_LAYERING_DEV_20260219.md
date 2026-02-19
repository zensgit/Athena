# Phase 71: Regression Gate Layering - Development

## Date
2026-02-19

## Background
- Existing delivery gate output was sequential but not explicitly layered, making CI signal triage slower.
- Failure logs were verbose and lacked normalized one-line spec summaries when Playwright failed.
- Gate failure handling also needed robust logging behavior across macOS bash environments.

## Goal
1. Split gate execution into clear layers: fast mocked vs integration/full-stack.
2. Add deterministic stage/layer summaries for CI readability.
3. Add concise failure extraction (failed spec lines or first error) for faster debugging.

## Changes

### 1) Phase5 mocked regression failure summary
- File: `scripts/phase5-regression.sh`
- Added:
  - ANSI-stripping helper for log parsing
  - `run_with_tee(...)` wrapper with safe exit-code handling under `set -euo pipefail`
  - failed spec summary extraction from Playwright logs
  - fallback first-error summary when failed-spec lines are unavailable
- On failure, script now prints normalized summary and the saved log path.

### 2) Delivery gate layered orchestration
- File: `scripts/phase5-phase6-delivery-gate.sh`
- Added `DELIVERY_GATE_MODE`:
  - `all` (default): run fast layer then integration layer
  - `mocked`: run only fast mocked layer
  - `integration`: run only integration/full-stack layer
- Added layered stage runner with per-stage log files and explicit PASS/FAIL status records.
- Added final layer summary output:
  - fast mocked layer results
  - integration/full-stack layer results

### 3) Failure diagnostics and exit semantics
- File: `scripts/phase5-phase6-delivery-gate.sh`
- Added per-stage failure parser:
  - prints one-line failed spec summaries when available
  - otherwise prints first error line
- Ensured failure exit codes propagate correctly (`FAIL(<rc>)`) and script exits non-zero on failed layers.
- Fixed macOS `mktemp` compatibility by using `XXXXXX`-suffix templates.

## Non-Functional Notes
- No backend/API contract changes.
- No frontend runtime behavior changes.
- Changes are limited to CI/operator scripts and log ergonomics.
