# Phase 112: Auth Storage-Restricted Recovery Event Coverage

## Date
2026-02-24

## Background
- Storage-restricted auth recovery mocked flow already existed in phase5 regression.
- It was not represented in structured `recovery_event` telemetry expected by guard summary.

## Goals
1. Emit structured recovery markers for storage-restricted auth recovery flow.
2. Add these markers to `phase5-regression` expected recovery event list.
3. Improve completeness of fail-fast recovery diagnostics.

## Changes

### 1) Marker emission in mocked storage-restricted auth flow
- `ecm-frontend/e2e/auth-storage-restricted-recovery.mock.spec.ts`
  - emits:
    - `recovery_event:auth_storage_restricted_browse_recovered`
    - `recovery_event:auth_storage_restricted_login_notice_visible`

### 2) Guard expected-event expansion
- `scripts/phase5-regression.sh`
  - expected recovery events list now includes:
    - `auth_storage_restricted_browse_recovered`
    - `auth_storage_restricted_login_notice_visible`

## Impact
- No runtime/product behavior change.
- Recovery telemetry/guard now explicitly covers storage-restricted auth recovery path.
