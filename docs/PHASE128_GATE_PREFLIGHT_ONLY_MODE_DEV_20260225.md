# Phase 128: Gate Preflight-Only Mode

## Date
2026-02-25

## Background
- Delivery gate currently supports `all|mocked|integration` modes.
- Teams need a lightweight mode to run only registry preflight checks without paying full mocked regression runtime.

## Goals
1. Add `preflight` gate mode for fast registry preflight execution only.
2. Keep existing `all|mocked|integration` behavior unchanged.
3. Preserve clear stage summary output for preflight-only runs.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - extends supported mode set:
    - `DELIVERY_GATE_MODE=preflight`
  - in fast layer:
    - always runs `mocked recovery registry preflight`
    - skips `mocked regression gate` when mode is `preflight`
    - prints:
      - `mocked regression stage skipped (preflight mode)`
  - unsupported mode message now includes `preflight`.

## Impact
- No runtime product behavior change.
- Faster pre-check option for local/CI workflows that only need registry consistency and deterministic sync checks.
