# Phase 114: Route Fallback Recovery Event Coverage

## Date
2026-02-25

## Background
- Unknown-route fallback mocked E2E cases were already in regression scope.
- Recovery telemetry guard still did not enforce these route-fallback paths explicitly.

## Goals
1. Emit structured recovery events for route fallback recovery endpoints.
2. Expand `phase5-regression` expected recovery set to include route fallback events.
3. Keep mocked delivery gate deterministic and green.

## Changes

### 1) Route fallback recovery markers
- `ecm-frontend/e2e/route-fallback-no-blank.mock.spec.ts`
  - emits:
    - `recovery_event:route_fallback_unauth_login_visible`
    - `recovery_event:route_fallback_auth_browse_visible`

### 2) Recovery guard expected set expansion
- `scripts/phase5-regression.sh`
  - expected recovery events list now includes:
    - `route_fallback_unauth_login_visible`
    - `route_fallback_auth_browse_visible`

## Impact
- No runtime product behavior change.
- Recovery guard now verifies unknown-route fallback coverage for both unauthenticated and authenticated flows.
