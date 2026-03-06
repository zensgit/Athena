# Phase 147: Delivery Gate Low-Confidence Recalibration Command Hints

## Date
2026-03-06

## Background
- Phase146 introduced strict recommendation confidence guards and LOW confidence diagnostics.
- When confidence is LOW, operators currently see threshold-relax commands, but often need to recalibrate recommendation sampling first.

## Goals
1. Add explicit recalibration hints for LOW-confidence hotspot/flaky recommendations.
2. Generate rerun commands that lower `*_RECOMMEND_MIN_SAMPLE` to observed sample counts.
3. Keep strict suggestion ordering deterministic and reason-prioritized.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - strict diagnostics hints:
    - add hotspot recalibration hint:
      - `Hotspot recalibration hint: rerun with recommend min sample <= <count>.`
    - add flaky-risk recalibration hint:
      - `Flaky-risk recalibration hint: rerun with recommend min sample <= <count>.`
  - strict command construction:
    - add `hotspot_recalibration_cmd` when:
      - hotspot recommendation confidence is LOW
      - observed hotspot recommendation sample count is a positive integer
    - add `flaky_recalibration_cmd` with the same conditions for flaky recommendation count
    - recalibration command keeps strict/recommend controls intact and sets:
      - `DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE=<observed_hotspot_count>`
      - `DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE=<observed_flaky_count>`
  - strict suggestion ordering:
    - for `hotspot_threshold`, output recalibration command before threshold-relax command
    - for `flaky_risk_threshold`, output recalibration command before threshold-relax command
  - fallback suggestion aggregation:
    - include recalibration commands in fallback list with existing dedupe behavior

## Compatibility
- Default behavior remains unchanged for high-confidence runs.
- Existing strict relax/recovery commands are preserved.
- Recalibration commands only appear when LOW confidence has valid observed counts.
