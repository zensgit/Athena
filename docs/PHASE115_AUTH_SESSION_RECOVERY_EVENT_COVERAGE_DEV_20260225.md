# Phase 115: Auth Session Recovery Event Coverage

## Date
2026-02-25

## Background
- Auth session recovery mocked flows were part of the regression suite.
- Recovery guard expected set did not explicitly include auth-session transient/terminal recovery outcomes.

## Goals
1. Emit structured recovery events for auth-session transient retry success path.
2. Emit structured recovery events for auth-session terminal redirect-to-login path.
3. Add these events to `phase5-regression` expected recovery set.

## Changes

### 1) Auth session recovery markers
- `ecm-frontend/e2e/auth-session-recovery.mock.spec.ts`
  - emits:
    - `recovery_event:auth_session_transient_retry_success`
    - `recovery_event:auth_session_terminal_redirect_login`

### 2) Recovery guard expected set expansion
- `scripts/phase5-regression.sh`
  - expected recovery events list now includes:
    - `auth_session_transient_retry_success`
    - `auth_session_terminal_redirect_login`

## Impact
- No runtime behavior change.
- Recovery telemetry guard now covers both recoverable and terminal auth-session 401 outcomes.
