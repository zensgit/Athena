# Phase 109: Delivery Gate Recovery Hint with Missing Event Names

## Date
2026-02-24

## Background
- Delivery gate startup diagnostics already detected recovery guard warning count.
- Existing hint text was generic and required manual log scanning to find which recovery events were missing.

## Goals
1. Enrich recovery-guard startup hint with concrete missing event names.
2. Keep backward-compatible fallback hint when names are unavailable.
3. Maintain zero impact on passing gate paths.

## Changes

### 1) Missing-event extraction in gate hints
- `scripts/phase5-phase6-delivery-gate.sh`
  - in `print_startup_failure_hints`:
    - parse `phase5_regression` output lines:
      - `- WARN missing event: <event_name>`
    - collect and deduplicate event names (up to 8 shown).
    - when available, print targeted hint:
      - `Recovery guard coverage appears incomplete. Missing events: ...`
    - otherwise keep prior generic guidance.

## Impact
- No runtime/product behavior changes.
- Improves operator triage speed when mocked regression fails with incomplete recovery event coverage.
