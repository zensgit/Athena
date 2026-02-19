# Phase 72: Failure Injection Coverage - Development

## Date
2026-02-19

## Background
- Auth/search recovery paths already had baseline coverage, but failure-injection scenarios were still fragmented.
- Day6 goal requires explicit coverage for:
  - transient refresh failures (no forced logout)
  - terminal refresh failures (logout/redirect)
  - temporary search backend failure followed by successful retry

## Goal
1. Strengthen deterministic failure-injection tests at unit and mocked E2E levels.
2. Validate both recoverable and terminal paths in auth recovery.
3. Validate user-facing retry flow for temporary search backend failures.

## Changes

### 1) Auth refresh classification injection coverage
- File: `ecm-frontend/src/services/authService.test.ts`
- Added refresh-failure payload scenarios:
  - transient `503` payload keeps session and avoids logout
  - terminal `403` payload triggers logout and clears local session state

### 2) API interceptor failure-injection coverage
- File: `ecm-frontend/src/services/api.test.ts`
- Added request-interceptor assertion helper and new scenarios:
  - transient request refresh failure logs `api.request.refresh.failed` and continues request without session-expired markers
  - terminal refresh throw during `401` recovery marks session expired and emits retry-failed + session-expired events

### 3) Auth session recovery mocked E2E matrix expansion
- File: `ecm-frontend/e2e/auth-session-recovery.mock.spec.ts`
- Refactored with shared mock setup helper and expanded to two scenarios:
  - transient `401` on first search request, second attempt succeeds (stays on `/search-results`)
  - unrecoverable `401` chain redirects to `/login` with session-expired guidance

### 4) Search retry recovery mocked E2E scenario
- File: `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
- Added scenario:
  - first query returns `503`
  - inline `Retry` action is used
  - second query succeeds and result card is rendered

## Non-Functional Notes
- No backend/API contract changes.
- No production runtime logic changes; this phase is test-coverage hardening only.
