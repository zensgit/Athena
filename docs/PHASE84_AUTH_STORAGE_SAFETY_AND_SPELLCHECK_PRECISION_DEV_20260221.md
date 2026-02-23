# Phase 84: Auth Storage Safety and Spellcheck Precision

## Date
2026-02-21

## Background
- Startup hardening is already in place, but `Login`/`PrivateRoute` still had direct `sessionStorage` reads/writes in hot paths.
- In restrictive browser contexts, storage APIs can throw and destabilize auth routing.
- Filename-like exact queries can still include wrapping punctuation/quotes, which can leak into spellcheck flow and create noisy “Did you mean” suggestions.
- `phase70` smoke failures for unavailable dependencies only surfaced raw curl timeout text.

## Goals
1. Make auth route/login storage access resilient in restricted contexts.
2. Tighten spellcheck skip heuristics for punctuated/quoted filename queries.
3. Improve `phase70` smoke diagnostics for backend/keycloak/UI preflight failures.

## Changes

### 1) Spellcheck precision hardening
- File: `ecm-frontend/src/utils/searchFallbackUtils.ts`
- Added query token normalization (`normalizeSearchToken`) before spellcheck/fallback decisions:
  - strips wrapping quote/pair punctuation
  - strips leading/trailing punctuation noise (`, ; : ! ?`)
- Applied normalization to:
  - `shouldSkipSpellcheckForQuery`
  - `shouldSuppressStaleFallbackForQuery`
- Result: exact filename-like queries such as `"e2e-preview-failure-*.bin"` and `(ui-e2e-*.pdf),` stay in precision mode and skip spellcheck.

### 2) Auth route storage safety hardening
- File: `ecm-frontend/src/components/auth/PrivateRoute.tsx`
- Added safe storage wrappers:
  - `safeSessionGetItem`
  - `safeSessionSetItem`
  - `safeSessionRemoveItem`
- Replaced direct `sessionStorage` calls in auth redirect state machine with safe wrappers.
- Result: restricted storage no longer throws through private-route auth flow.

### 3) Login storage safety hardening
- File: `ecm-frontend/src/components/auth/Login.tsx`
- Added safe wrappers for both session/local storage:
  - `safeSessionGetItem`
  - `safeSessionRemoveItem`
  - `safeLocalGetItem`
  - `safeLocalRemoveItem`
- Replaced direct storage access for:
  - init message derivation
  - stale marker cleanup
  - manual sign-in pre-cleanup
- Result: login CTA remains usable even when storage cleanup operations throw.

### 4) Phase70 preflight diagnostics hardening
- File: `scripts/phase70-auth-route-matrix-smoke.sh`
- Added `check_endpoint` helper with explicit failure message and hint.
- Replaced raw curl checks for:
  - backend health
  - keycloak discovery
  - UI reachability
- Result: environment failures are now classified with actionable hints instead of opaque timeout logs.

## Test Updates
- `ecm-frontend/src/utils/searchFallbackUtils.test.ts`
  - added quoted/punctuated filename spellcheck-skip coverage.
- `ecm-frontend/src/components/auth/PrivateRoute.test.tsx`
  - added restricted-sessionStorage throw scenario.
- `ecm-frontend/src/components/auth/Login.test.tsx`
  - added manual sign-in path coverage when storage remove throws.

## Impact
- No backend API contract changes.
- No route contract changes.
- Improves auth path survivability under restrictive browser/runtime storage behavior.
- Reduces false-positive spellcheck suggestions for exact filename searches.
