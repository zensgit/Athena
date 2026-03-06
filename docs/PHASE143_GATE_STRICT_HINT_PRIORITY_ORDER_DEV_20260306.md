# Phase 143: Delivery Gate Strict Hint Priority Ordering

## Date
2026-03-06

## Background
- Strict failure hints already exposed reasons and remediation commands.
- Command list ordering was implicit and could vary by available signals, reducing operator efficiency.

## Goals
1. Output strict remediation commands in explicit priority order based on failure reasons.
2. Deduplicate overlapping commands while preserving reason-driven ordering.
3. Keep successful path output unchanged.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - strict hint block now builds candidate commands first:
    - `recovery_cmd`
    - `hotspot_cmd`
    - `flaky_cmd`
  - orders command output by parsed strict failure reasons:
    - `recovery_guard`
    - `hotspot_threshold`
    - `flaky_risk_threshold`
  - appends fallback candidates and deduplicates before printing.
  - prints section:
    - `Suggested commands (priority order):`
    - numbered list for direct operator execution.

## Compatibility
- Passing runs remain unchanged.
- Failing runs now provide deterministic command ordering for faster triage.
