# Phase 85: Auth Storage-Restricted Mock E2E

## Date
2026-02-21

## Background
- Day1 hardened `Login`/`PrivateRoute` storage access with safe wrappers.
- A dedicated mocked E2E was still missing for combined session/local storage read restrictions.

## Goal
1. Add mocked E2E proving auth-recovery flows remain usable when auth-related storage keys are partially blocked.
2. Include the scenario in default mocked regression gate.

## Changes

### 1) New mocked spec
- File: `ecm-frontend/e2e/auth-storage-restricted-recovery.mock.spec.ts`
- Scenario coverage:
  - seed bypass auth session
  - patch `sessionStorage.getItem` for auth keys to throw `SecurityError`
  - patch `localStorage.getItem` for redirect-reason key to throw `SecurityError`
  - verify `/browse/root` renders app shell (non-blank)
  - verify `/login?reason=session_expired` still shows session-expired guidance and login CTA

### 2) Gate integration
- File: `scripts/phase5-regression.sh`
- Added new spec entry:
  - `e2e/auth-storage-restricted-recovery.mock.spec.ts`
- Mocked regression case count changed from `16` to `17`.

## Impact
- No backend contract changes.
- Adds deterministic regression coverage for storage-restricted auth contexts.
