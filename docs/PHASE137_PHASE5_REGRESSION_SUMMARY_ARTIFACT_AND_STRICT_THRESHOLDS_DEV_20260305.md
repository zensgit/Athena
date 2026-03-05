# Phase 137: Phase5 Regression Summary Artifact and Strict Threshold Controls

## Date
2026-03-05

## Background
- `scripts/phase5-regression.sh` already prints human-readable hotspot/flaky/recovery diagnostics.
- CI and operators still lacked a stable machine-readable artifact for trend and gating analysis.
- Strict mode only guarded recovery events; hotspot/flaky signal thresholds were not enforceable.

## Goals
1. Add optional JSON summary artifact output for `phase5-regression`.
2. Persist key diagnostics sections in a deterministic machine-readable schema.
3. Add optional strict threshold controls for hotspot duration and flaky-risk score.
4. Keep default behavior backward compatible when new env vars are unset.

## Changes
- `scripts/phase5-regression.sh`
  - added env controls:
    - `PHASE5_REGRESSION_SUMMARY_JSON`
    - `PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD`
    - `PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD`
  - added summary writer with schema payload (`schema_version: 1`):
    - run metadata (start/end/duration/spec count/ui target/project/workers)
    - strict mode + strict threshold controls + strict failure reasons
    - duration hotspots and flaky-risk candidates
    - retry signal count
    - startup SLA warning/drift counts
    - recovery guard warning/missing/unexpected/event counts
    - final `exit_status`
  - added exit trap to always attempt artifact write when path is configured.
  - timing parser now emits deterministic JSON analysis and optional strict threshold checks:
    - in strict mode, run fails when configured threshold matches are detected.
  - retained default compatibility:
    - no artifact written unless `PHASE5_REGRESSION_SUMMARY_JSON` is set
    - no additional strict failures unless thresholds are configured.

## Compatibility
- Existing `phase5-regression` invocation remains unchanged by default.
- Delivery gate script remains compatible (no required interface change).
