# Phase 145: Delivery Gate Strict Percentile Config Passthrough

## Date
2026-03-06

## Background
- Phase144 introduced percentile-based strict remediation recommendations.
- Percentiles/padding/step were fixed defaults in scripts, limiting operator control in CI/local diagnosis.

## Goals
1. Make strict recommendation percentile controls configurable from delivery gate.
2. Pass configuration through to `phase5-regression` consistently (mocked regression + preflight + recovery rerun hints).
3. Surface configuration in execution plan and startup logs for traceability.

## Changes
- `scripts/phase5-regression.sh`
  - add configurable env defaults:
    - `PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE` (default `0.95`)
    - `PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC` (default `0.1`)
    - `PHASE5_STRICT_FLAKY_RISK_RECOMMEND_PERCENTILE` (default `0.9`)
    - `PHASE5_STRICT_FLAKY_RISK_RECOMMEND_STEP` (default `1`)
  - apply config in percentile computation:
    - hotspot: `pX(duration_sec) + padding_sec`
    - flaky-risk: `max(ceil(pY(score)), strict_threshold + step|step)`
  - include config metadata in summary artifact strict section.
  - emit recommendation logs with configured percentile labels (`p80`, `p60`, etc.).

- `scripts/phase5-phase6-delivery-gate.sh`
  - add gate-scoped controls with env/inherited/default resolution:
    - `DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE`
    - `DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC`
    - `DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE`
    - `DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP`
  - add CLI flags:
    - `--phase5-hotspot-recommend-percentile=<num>`
    - `--phase5-hotspot-recommend-padding-sec=<num>`
    - `--phase5-flaky-recommend-percentile=<num>`
    - `--phase5-flaky-recommend-step=<int>`
  - validate percentile range `(0,1]` and numeric formats for new controls.
  - include new controls in execution plan text/json payload.
  - pass controls into:
    - mocked regression stage
    - mocked registry preflight (`PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1`)
    - strict recovery rerun hint command.

## Compatibility
- Default behavior unchanged (`0.95/0.1/0.9/1`).
- Existing invocations without new flags continue to work.
