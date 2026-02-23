# Phase 96: Startup SLA Drift Baseline Warnings

## Date
2026-02-23

## Background
- Phase95 introduced startup SLA `OK/WARN` summary and warning count.
- Operators also need drift context against stable baseline to distinguish threshold breaches from gradual latency regression.

## Goals
1. Add startup SLA drift-vs-baseline summary in `phase5-regression`.
2. Add drift warning count signal for downstream gate diagnosis.
3. Feed drift warning signal into delivery gate startup failure hints.

## Changes

### 1) Drift baseline analysis in mocked regression
- File: `scripts/phase5-regression.sh`
- Added:
  - `phase5_regression: startup SLA drift vs baseline`
  - `phase5_regression: startup SLA drift warning count: N`
- Baseline resolution:
  - Env override:
    - `ECM_STARTUP_SLA_BASELINE_LOGIN_VISIBLE_MS` / `ECM_STARTUP_SLA_BASELINE_LOGIN_MS`
    - `ECM_STARTUP_SLA_BASELINE_BROWSE_VISIBLE_MS` / `ECM_STARTUP_SLA_BASELINE_BROWSE_MS`
  - Defaults:
    - `login_visible=1500ms`
    - `browse_visible=1800ms`
  - Fallback (unknown route key): `max(500, threshold * 0.2)`
- Drift warning rule:
  - `elapsed - baseline >= 700ms` OR `elapsed / baseline >= 1.35`

### 2) Delivery gate hint aggregation for drift warnings
- File: `scripts/phase5-phase6-delivery-gate.sh`
- Added detection:
  - `phase5_regression: startup SLA drift warning count: [1-9]+`
- Added startup hint:
  - `Startup latency drift warnings detected. Compare against baseline and investigate runtime variance/regression.`

## Impact
- No backend/API contract changes.
- Provides baseline-aware startup regression signal in mocked gate and delivery-gate failure analysis.
