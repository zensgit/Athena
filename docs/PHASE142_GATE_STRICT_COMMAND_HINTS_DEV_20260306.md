# Phase 142: Delivery Gate Strict Command Hints

## Date
2026-03-06

## Background
- Gate now supports phase5 strict threshold passthrough and summary artifact parsing.
- Operator output still lacked direct next-step commands when strict threshold failures happen.

## Goals
1. Add actionable command hints when strict threshold failures are detected.
2. Include summary-derived strict details (hotspot/flaky/recovery) in hints.
3. Preserve default successful output behavior.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - extended strict summary parsing in startup failure hints:
    - parses `recovery_missing` / `recovery_unexpected` from summary payload.
  - added strict failure command suggestions:
    - hotspot triage command with relaxed threshold suggestion
    - flaky-risk triage command with relaxed threshold suggestion
    - recovery-guard deep-dive command (when reason includes `recovery_guard`)
  - retains existing strict hint lines and merges with command-based guidance.

## Compatibility
- No behavior change on passing runs.
- On strict failures, output is additive and more actionable.
