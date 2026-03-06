# Phase 149: Gate Strict Suggestions Artifact and Confidence Metadata

## Date
2026-03-06

## Background
- Strict remediation suggestions in delivery gate were previously text-only.
- Phase146/147 introduced confidence/low-sample controls, but machine consumers still had to parse logs.

## Goals
1. Add structured strict suggestion artifact output for CI and tooling.
2. Add normalized confidence metadata to phase5 regression strict recommendation summary.
3. Keep existing output fields backward-compatible.

## Changes
- `scripts/phase5-regression.sh`
  - strict recommendation metadata extended for hotspot and flaky-risk:
    - `*_recommendation_confidence_level` (`HIGH|LOW`)
    - `*_recommendation_reason_code` (`sufficient_sample|sample_below_min|sample_empty`)
    - `*_recommended_min_sample`
  - metadata is computed for all three sample states:
    - sufficient sample: `HIGH`, `sufficient_sample`
    - low sample (`0 < count < min`): `LOW`, `sample_below_min`, recommended min-sample set to observed count
    - empty sample (`count = 0`): `LOW`, `sample_empty`
  - existing fields retained:
    - `*_recommendation_low_confidence`
    - `*_recommended_count`
    - threshold/percentile/sample fields

- `scripts/phase5-phase6-delivery-gate.sh`
  - parse new strict metadata from summary artifact:
    - confidence level, reason code, recommended min-sample
  - strict hints enriched with reason code:
    - e.g. `Hotspot recommendation confidence: LOW (...) [reason=sample_below_min].`
  - recalibration hint/command emission now keyed by reason:
    - only emit recalibration path for `sample_below_min`
  - add structured strict suggestion payload builder:
    - includes priority, reason, action, scope, command
    - schema: `schema_version: 1`
  - add output controls:
    - `DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON=1|0`
    - `DELIVERY_GATE_STRICT_SUGGESTIONS_FILE=<path>`
    - CLI flags:
      - `--print-strict-suggestions-json`
      - `--no-print-strict-suggestions-json`
      - `--strict-suggestions-file=<path>`
  - execution plan payload includes strict suggestion output controls.

## Compatibility
- Existing strict-hint text output remains.
- Existing summary artifact fields remain.
- New metadata/JSON outputs are additive and optional.
