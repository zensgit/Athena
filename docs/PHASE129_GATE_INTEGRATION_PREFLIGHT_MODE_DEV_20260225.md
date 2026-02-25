# Phase 129: Gate Integration-Preflight Mode

## Date
2026-02-25

## Background
- Existing gate modes:
  - `preflight`: registry preflight only
  - `integration`: integration layer only
  - `all`: mocked + integration
- Missing mode for "registry preflight + integration, but skip mocked regression".

## Goals
1. Add a dedicated `integration-preflight` mode.
2. Ensure fast-layer registry preflight runs before integration layer.
3. Skip mocked regression stage in this mode to reduce runtime.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - mode set extended:
    - `all|mocked|integration|preflight|integration-preflight`
  - fast layer now executes in `integration-preflight` mode.
  - mocked regression stage is skipped when mode is:
    - `preflight`
    - `integration-preflight`
  - integration layer scheduling updated:
    - run integration when mode is `integration-preflight` and fast preflight passes.
    - skip integration with explicit message if fast preflight fails.

## Impact
- No runtime product behavior change.
- Adds a practical CI/local path for quick registry validation before full integration checks, without running mocked suite.
