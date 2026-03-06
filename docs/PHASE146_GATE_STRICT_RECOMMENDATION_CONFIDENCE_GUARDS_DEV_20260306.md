# Phase 146: Delivery Gate Strict Recommendation Confidence Guards

## Date
2026-03-06

## Background
- Strict percentile recommendations in Phase145 are configurable.
- Small samples can make percentile recommendations unstable and overfit a single run.

## Goals
1. Add minimum-sample controls for strict recommendation confidence.
2. Mark low-confidence recommendations in phase5 summary and gate hints.
3. Keep command suggestions deterministic while making fallback behavior explicit.

## Changes
- `scripts/phase5-regression.sh`
  - new env controls:
    - `PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE` (default `5`)
    - `PHASE5_STRICT_FLAKY_RISK_RECOMMEND_MIN_SAMPLE` (default `3`)
  - strict recommendation analysis now records:
    - `*_recommended_count`
    - `*_recommendation_low_confidence`
  - low-confidence fallback behavior:
    - hotspot: if `count < min_sample` and strict threshold exists, fallback to `strict + padding`
    - flaky-risk: if `count < min_sample` and strict threshold exists, fallback to `strict + step`
  - logs emit explicit low-confidence/fallback lines.
  - summary artifact strict section includes min-sample config and confidence fields.

- `scripts/phase5-phase6-delivery-gate.sh`
  - new gate controls + CLI flags:
    - `DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE`
    - `DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE`
    - `--phase5-hotspot-recommend-min-sample=<int>`
    - `--phase5-flaky-recommend-min-sample=<int>`
  - add positive-integer validation for both min-sample controls.
  - execution-plan text/json now includes both min-sample fields.
  - strict summary extraction/hints now parse and print low-confidence details:
    - `Hotspot recommendation confidence: LOW (...)`
    - `Flaky-risk recommendation confidence: LOW (...)`
  - propagate min-sample controls into:
    - mocked regression stage
    - mocked registry preflight (`registry-only`)
    - strict recovery rerun command hint.

## Compatibility
- Default path remains unchanged for normal sample counts.
- Existing users can ignore new controls and keep default behavior.
