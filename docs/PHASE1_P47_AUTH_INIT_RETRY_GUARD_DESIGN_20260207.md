# Phase 1 P47 - Auth Init Retry Guard Design (2026-02-07)

## Background
- P45/P46 fixed timeout handling and login feedback.
- Remaining gap: transient Keycloak/network glitches on first `check-sso` attempt still push user into fallback path immediately.

## Goal
- Absorb one transient auth init failure automatically before declaring bootstrap failure.
- Keep fallback deterministic and bounded by timeout.

## Scope
- Frontend only:
  - `ecm-frontend/src/services/authBootstrap.ts`
  - `ecm-frontend/src/index.tsx`
  - unit tests

## Design Changes

### 1) Retry-capable auth bootstrap utility
- Added `runAuthInitWithRetry(taskFactory, options)`:
  - wraps each attempt with `withAuthInitTimeout`,
  - supports `maxAttempts` (default `2`),
  - supports `retryDelayMs` (default `800`),
  - supports `onRetry(attempt, error)` callback for logging.

### 2) Startup flow integration
- `index.tsx` now calls `runAuthInitWithRetry` instead of one-shot timeout wrapper.
- Added env knobs:
  - `REACT_APP_AUTH_INIT_MAX_ATTEMPTS` (default `2`)
  - `REACT_APP_AUTH_INIT_RETRY_DELAY_MS` (default `800`)
  - existing `REACT_APP_AUTH_INIT_TIMEOUT_MS` still applies per attempt.
- Retry attempts emit warning logs; final failure still follows existing timeout/error status path from P46.

### 3) Test coverage
- Expanded `authBootstrap` tests:
  - retry then success,
  - fail after max attempts,
  - timeout on first attempt then successful retry.

## Compatibility
- No backend or API changes.
- Existing fallback behavior unchanged when retries are exhausted.
