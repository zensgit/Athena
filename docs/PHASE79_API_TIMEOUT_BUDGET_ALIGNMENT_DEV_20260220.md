# Phase 79: API Timeout Budget Alignment and Timeout Recovery

## Date
2026-02-20

## Background
- API client timeout behavior was not standardized by operation class.
- Some requests could wait excessively long without clear timeout warning behavior.

## Goal
1. Standardize frontend API timeout defaults by operation class.
2. Keep long-running operations (upload/download) on larger timeout budgets.
3. Add explicit timeout recovery path (safe retry + warning).

## Changes

### 1) Timeout budget constants
- File: `ecm-frontend/src/constants/network.ts`
- Added configurable timeout budgets:
  - `REACT_APP_API_TIMEOUT_READ_MS` (default `15000`)
  - `REACT_APP_API_TIMEOUT_WRITE_MS` (default `20000`)
  - `REACT_APP_API_TIMEOUT_UPLOAD_MS` (default `120000`)
  - `REACT_APP_API_TIMEOUT_DOWNLOAD_MS` (default `120000`)
- Added `resolvePositiveIntEnv` normalization helper for positive integer env parsing.

### 2) API service timeout and timeout-recovery behavior
- File: `ecm-frontend/src/services/api.ts`
- Added timeout budget application per method class:
  - `get` => read timeout
  - `post/put/patch/delete` => write timeout
  - `getBlob` + `downloadFile` => download timeout
  - `uploadFile` => upload timeout
- Preserves explicit per-request timeout override (`config.timeout`) when provided.
- Added timeout recovery path in response interceptor:
  - For timeout errors (`ECONNABORTED` or timeout message), safe methods (`GET/HEAD/OPTIONS`) retry once.
  - If timeout remains unrecovered, show warning toast:
    - `Request timed out. Please retry.`
- Added timeout-related diagnostics events:
  - `api.response.timeout.retry.start`
  - `api.response.timeout.retry.success`
  - `api.response.timeout.retry.failed`
  - `api.response.timeout.warning`

### 3) Regression test hardening for watchdog mocked scenario
- File: `ecm-frontend/e2e/filebrowser-loading-watchdog.mock.spec.ts`
- Updated hanging request simulation to bounded slow responses (13.5s) so watchdog behavior is validated without conflicting with new API timeout defaults.
- Updated retry loop logic to `expect.poll`-driven stabilization and strict-mode-safe empty-state assertion (`.first()`).

### 4) API unit tests expanded
- File: `ecm-frontend/src/services/api.test.ts`
- Added coverage for:
  - axios default read timeout initialization
  - timeout retry success for GET
  - timeout warning path when retry cannot recover
  - per-operation timeout budgets
  - explicit timeout override precedence

## Non-Functional Notes
- No backend contract changes.
- Timeout behavior is now deterministic and configurable by env, with safer user-facing recovery messaging.
