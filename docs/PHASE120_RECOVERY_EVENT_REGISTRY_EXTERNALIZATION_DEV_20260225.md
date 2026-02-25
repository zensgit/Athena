# Phase 120: Recovery Event Registry Externalization

## Date
2026-02-25

## Background
- `phase5-regression` expected recovery events were hard-coded in script.
- As coverage grows, manual script edits increase drift risk between E2E `recovery_event:*` markers and guard expected set.

## Goals
1. Externalize expected recovery events into a dedicated registry file.
2. Add pre-run registry consistency check against mocked regression spec markers.
3. Keep strict-mode compatibility with existing guard behavior.

## Changes

### 1) External expected-events registry
- Added:
  - `ecm-frontend/e2e/recovery-events.expected.txt`
  - canonical expected `recovery_event` names for `PHASE5_SPECS`.

### 2) `phase5-regression` registry validation
- `scripts/phase5-regression.sh`
  - new envs:
    - `PHASE5_RECOVERY_EVENTS_FILE` (default: `e2e/recovery-events.expected.txt`)
    - `PHASE5_RECOVERY_REGISTRY_STRICT` (default follows `PHASE5_RECOVERY_GUARD_STRICT`)
  - new pre-run check:
    - compares observed `recovery_event:*` markers from `PHASE5_SPECS` vs registry entries.
    - reports mismatch details:
      - marker missing from events file
      - events-file entry not found in specs
    - strict mode can fail fast on mismatch.

### 3) Guard expected-set loading
- `scripts/phase5-regression.sh` Node summary now loads expected events from `PHASE5_RECOVERY_EVENTS_FILE` instead of hard-coded array.
- Script prints:
  - `phase5_regression: recovery expected events source: ...`

## Impact
- No product runtime behavior change.
- Recovery guard config is now centralized and easier to maintain as E2E coverage expands.
