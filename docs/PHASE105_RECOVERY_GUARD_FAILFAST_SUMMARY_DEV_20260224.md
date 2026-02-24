# Phase 105: Recovery Guard Summary in Fail-Fast Runs

## Date
2026-02-24

## Background
- Phase104 added `recovery_event:*` marker aggregation in `phase5-regression`.
- The summary/guard block was only printed when at least one marker existed.
- In early-failure or partial-run scenarios without markers, delivery-gate diagnostics could miss explicit recovery-coverage status.

## Goals
1. Always print recovery summary and guard status, even when zero markers are captured.
2. Keep output shape deterministic for downstream gate hint parsing.
3. Preserve existing behavior for green runs where all markers are present.

## Changes

### 1) Deterministic recovery summary output
- `scripts/phase5-regression.sh`
  - moved recovery summary and guard evaluation outside of marker-present conditional.
  - now always prints:
    - `phase5_regression: recovery events`
    - `phase5_regression: recovery guard status`
    - `phase5_regression: recovery guard warning count: N`
  - when no markers exist, explicitly prints:
    - ` - (none)`
    - per-expected-event WARN lines
    - non-zero warning count

### 2) Gate hint compatibility
- No interface change needed in `scripts/phase5-phase6-delivery-gate.sh`.
- Existing hint trigger remains valid:
  - `phase5_regression: recovery guard warning count: [1-9]+`

## Impact
- No frontend/backend runtime behavior change.
- Improves operability and triage quality for failed or truncated mocked regression runs.
