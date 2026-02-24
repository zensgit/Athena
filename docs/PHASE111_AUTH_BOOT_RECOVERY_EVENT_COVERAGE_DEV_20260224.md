# Phase 111: Auth Boot Watchdog Recovery Event Coverage

## Date
2026-02-24

## Background
- Recovery guard already covered app-error, chunk-load, and startup-fallback paths.
- Auth boot watchdog recovery flow lacked explicit `recovery_event` markers in mocked regression telemetry.

## Goals
1. Emit structured recovery events for auth boot watchdog recovery path.
2. Include auth boot watchdog events in `phase5-regression` expected recovery guard set.
3. Keep mocked gate output deterministic and complete.

## Changes

### 1) Auth boot watchdog marker emission
- `ecm-frontend/e2e/auth-boot-watchdog-recovery.mock.spec.ts`
  - emits:
    - `recovery_event:auth_boot_watchdog_alert_shown`
    - `recovery_event:auth_boot_watchdog_continue_login`

### 2) Recovery guard expected set expansion
- `scripts/phase5-regression.sh`
  - expected recovery events list now includes:
    - `auth_boot_watchdog_alert_shown`
    - `auth_boot_watchdog_continue_login`

## Impact
- No backend/API/runtime behavior changes.
- Recovery guard telemetry now covers auth boot watchdog recovery, reducing blind spots in fail-fast diagnostics.
