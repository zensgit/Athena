# Phase 134: App Error Recovery Login Hardening

## Date
2026-03-05

## Background
- `AppErrorBoundary` already provides `Reload` and `Back to Login`.
- In repeated runtime-failure flows, stale redirect cooldown markers may survive and reduce recovery quality.

## Goals
1. Ensure "Back to Login" path resets redirect-failure cooldown markers.
2. Route recovery login with cache-busting query to reduce stale-asset risk.
3. Keep existing fallback UI contract unchanged.

## Changes
- `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
  - new helper:
    - `buildRecoveryLoginUrl(href, reason)`
  - `handleGoToLogin` now:
    - clears `AUTH_REDIRECT_FAILURE_COUNT_KEY`
    - clears `AUTH_REDIRECT_LAST_FAILURE_AT_KEY`
    - navigates via cache-busted recovery URL:
      - `/login?reason=app_recovery&_ecm_reload=<timestamp>`
- `ecm-frontend/src/components/layout/AppErrorBoundary.test.tsx`
  - added unit coverage for `buildRecoveryLoginUrl`.

## Impact
- Recovery from unexpected runtime errors is more deterministic.
- Prevents stale redirect cooldown state from interfering with post-recovery sign-in.
