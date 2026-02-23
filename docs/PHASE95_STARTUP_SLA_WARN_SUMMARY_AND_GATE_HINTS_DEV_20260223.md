# Phase 95: Startup SLA WARN Summary + Gate Failure Hints

## Date
2026-02-23

## Background
- Phase94 added startup visibility SLA samples in mocked regression output.
- Raw samples are useful, but operators still need an explicit `OK/WARN` summary and failure-hint linkage in delivery gate output.

## Goals
1. Convert startup SLA sample lines to structured `OK/WARN` summary in `phase5-regression`.
2. Surface startup SLA warning signal in `phase5-phase6-delivery-gate` failure hints.
3. Keep parsing robust and avoid false positives from stack-trace source snippets.

## Changes

### 1) Startup SLA status summarization
- File: `scripts/phase5-regression.sh`
- Added:
  - `phase5_regression: startup SLA status` section:
    - `OK` when latency is below warning threshold.
    - `WARN` when route latency exceeds threshold or is near threshold (`>= 80%` of configured threshold).
  - `phase5_regression: startup SLA warning count: N`
- Parsing hardening:
  - only accept strict structured sample lines:
    - `startup_sla:<name>_ms=<elapsed>:threshold_ms=<threshold>`
  - exclude stack traces / source excerpt noise.

### 2) Delivery gate startup hint integration
- File: `scripts/phase5-phase6-delivery-gate.sh`
- Added startup hint trigger:
  - detect `phase5_regression: startup SLA warning count: [1-9]+` in failed stage logs.
- New failure hint line:
  - `Startup visibility SLA warnings detected. Review 'phase5_regression: startup SLA status' for near-threshold routes.`

## Impact
- No backend/API contract changes.
- Faster operator triage for startup slow-path regression in failed mocked gates.
