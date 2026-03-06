# Phase 140: Delivery Gate Phase5 Strict Threshold Passthrough

## Date
2026-03-06

## Background
- `phase5-regression` already supports strict controls:
  - `PHASE5_RECOVERY_GUARD_STRICT`
  - `PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD`
  - `PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD`
- Delivery gate previously required callers to inject these phase5-prefixed env vars directly, lacking a gate-scoped interface.

## Goals
1. Add gate-scoped strict controls and CLI flags.
2. Ensure values are visible in execution plan and startup logs.
3. Pass values deterministically into mocked phase5 preflight/regression stages.
4. Keep defaults backward compatible.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - added env controls:
    - `DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT`
    - `DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD`
    - `DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD`
  - added CLI flags:
    - `--phase5-strict-recovery-guard=<0|1>`
    - `--phase5-strict-hotspot-sec=<num>`
    - `--phase5-strict-flaky-score=<int>`
  - source-resolution behavior:
    - `cli` > `env` > inherited phase5 env > local default
  - execution plan payload now includes strict threshold fields.
  - mocked preflight/regression stage now forwards resolved values to phase5 env vars.
  - added argument validation:
    - recovery strict: `0|1`
    - hotspot threshold: non-negative number
    - flaky score threshold: non-negative integer

## Compatibility
- Default values remain `0`, so prior behavior is unchanged without new flags/env.
- Existing gate modes and stage ordering remain unchanged.
