# Phase 144: Delivery Gate Strict Percentile Recommendations

## Date
2026-03-06

## Background
- Strict failure hints already provided remediation commands.
- Previous remediation thresholds were static offsets (`hotspot +2.0s`, `flaky +1`) and not tied to run distribution.

## Goals
1. Generate strict-threshold remediation targets from current run percentiles.
2. Persist recommendation metadata into phase5 summary artifact for gate consumption.
3. Keep backward compatibility by retaining static offset fallback when percentile fields are missing.

## Changes
- `scripts/phase5-regression.sh`
  - add percentile helpers in timing summary analysis.
  - compute and emit:
    - hotspot recommendation: `p95(duration_sec) + 0.1s` (rounded to 0.1s)
    - flaky-risk recommendation: `max(ceil(p90(score)), strict+1)`
  - write recommendation fields into summary artifact:
    - `strict_threshold_controls.hotspot_recommended_threshold`
    - `strict_threshold_controls.hotspot_recommended_percentile`
    - `strict_threshold_controls.hotspot_recommended_sample`
    - `strict_threshold_controls.flaky_recommended_threshold`
    - `strict_threshold_controls.flaky_recommended_percentile`
    - `strict_threshold_controls.flaky_recommended_sample`
  - print recommendation lines in regression output for operator traceability.

- `scripts/phase5-phase6-delivery-gate.sh`
  - extend summary parser extraction with new recommended-threshold keys.
  - print strict hint details showing percentile basis (`p95`/`p90`) and sampled values.
  - prefer recommended thresholds when generating remediation commands, fallback to legacy static offsets if missing.
  - harden strict command ordering by fixed reason priority:
    - `recovery_guard` -> `hotspot_threshold` -> `flaky_risk_threshold`

## Compatibility
- Legacy summaries (without recommended fields) still work via existing fallback logic.
- Success path remains unchanged (`phase5_phase6_delivery_gate: ok`).
