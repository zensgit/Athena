# Phase 80: Startup Chaos Matrix Expansion

## Date
2026-02-20

## Background
- Startup/auth matrix already covered session-expired, redirect-pause, unknown-route fallback, and storage remove guard.
- Remaining chaos gaps: storage read restriction path and stale login-marker jitter cleanup.

## Goal
1. Expand auth-route matrix with additional startup chaos scenarios.
2. Keep terminal states recoverable (login or keycloak), not blank.
3. Keep matrix runnable under existing smoke script.

## Changes

### 1) Matrix scenario expansion
- File: `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
- Added helpers:
  - `injectLocalStorageGetGuard`
  - `seedStaleLoginInProgress`
- Added scenarios:
  - `login reason fallback remains visible when localStorage redirect reason read throws`
  - `login clears stale in-progress markers under redirect timing jitter`

### 2) Existing matrix retained and revalidated
- Existing scenarios remain:
  - session-expired URL guidance
  - redirect-pause guidance (protected-route + direct-login)
  - unknown-route no-blank fallback
  - startup recoverability when sessionStorage remove throws
  - login CTA terminal redirect to keycloak auth endpoint

## Non-Functional Notes
- No backend contract changes.
- Smoke script `scripts/phase70-auth-route-matrix-smoke.sh` continues to execute the expanded matrix without script changes.
