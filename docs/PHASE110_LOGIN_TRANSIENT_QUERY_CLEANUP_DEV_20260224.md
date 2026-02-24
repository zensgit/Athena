# Phase 110: Login Transient Recovery Query Cleanup

## Date
2026-02-24

## Background
- Login page previously removed the entire query string when `reason` existed.
- This could drop unrelated query parameters.
- After introducing cache-busting reload (`_ecm_reload`), login URL could retain transient recovery noise.

## Goals
1. Remove only transient recovery params on login (`reason`, `_ecm_reload`).
2. Preserve unrelated query params and hash.
3. Keep chunk/startup cache-bust E2E assertions aligned with cleanup behavior.

## Changes

### 1) Targeted query cleanup in Login
- `ecm-frontend/src/components/auth/Login.tsx`
  - added `RECOVERY_CACHE_BUST_PARAM = '_ecm_reload'`.
  - added `clearTransientRecoveryQueryParams()`:
    - removes `reason` and `_ecm_reload`
    - preserves other search params and hash
  - mount effect now calls targeted cleanup when either transient param exists.

### 2) Unit coverage expansion
- `ecm-frontend/src/components/auth/Login.test.tsx`
  - added:
    - preserve-other-query test (`reason` removed, `source` kept, hash kept)
    - `_ecm_reload` cleanup test (`source` kept)

### 3) E2E alignment for cache-bust + cleanup
- `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
- `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts`
  - updated reload assertions to:
    - wait for `_ecm_reload` URL to occur
    - then assert it is cleaned from final URL

## Impact
- No backend/API changes.
- Improves URL hygiene and avoids unintended query loss while retaining recovery diagnostics semantics.
