# Phase 133: Gate Execution Plan File Artifact

## Date
2026-03-05

## Background
- Gate already supports execution-plan preview in text/json.
- CI and local automation still needed a stable artifact output path without parsing full console logs.

## Goals
1. Add a file-output channel for execution plan payload.
2. Keep existing stdout behavior compatible.
3. Add schema marker for machine-consumption stability.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - added env:
    - `DELIVERY_GATE_EXECUTION_PLAN_FILE`
  - added CLI flag:
    - `--plan-file=<path>`
  - execution plan payload now includes:
    - JSON: `"schema_version": 1`
    - text: `schema version: 1`
  - added artifact writer:
    - auto-creates parent directory
    - emits `wrote execution plan => <path>`
  - plan payload generation now triggers when any of below is true:
    - `DELIVERY_GATE_PRINT_EXECUTION_PLAN=1`
    - `--plan`
    - `DELIVERY_GATE_EXECUTION_PLAN_FILE` is configured
  - added guard:
    - fails fast when `DELIVERY_GATE_EXECUTION_PLAN_FILE` points to a directory.

## Impact
- CI can now consume deterministic plan artifacts directly from file.
- Existing operator flow remains unchanged unless `--plan-file`/env is used.
