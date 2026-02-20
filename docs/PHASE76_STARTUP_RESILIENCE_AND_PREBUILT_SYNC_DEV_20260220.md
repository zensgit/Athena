# Phase 76: Startup Resilience and Prebuilt Sync Dirty-Worktree Guard

## Date
2026-02-20

## Background
- User-observed startup blank/boot-stuck behavior can occur when startup storage operations throw unexpectedly.
- Static proxy smoke flow could report “prebuilt up-to-date” while frontend has uncommitted source edits, causing stale static validation.

## Goals
1. Make auth bootstrap startup path resilient to storage-operation failures.
2. Ensure startup always transitions to a recoverable terminal state.
3. Make `ECM_SYNC_PREBUILT_UI=auto` detect frontend dirty worktree and rebuild prebuilt static bundle.

## Changes

### 1) Startup storage safety + fatal guard
- File: `ecm-frontend/src/index.tsx`
- Added safe sessionStorage helpers:
  - `safeSessionGetItem`
  - `safeSessionSetItem`
  - `safeSessionRemoveItem`
- Updated startup helpers to use safe storage operations:
  - `clearLoginProgress`
  - `clearAuthInitStatus`
  - `setAuthInitStatus`
- Moved `clearAuthInitStatus({ preserveSessionExpired: true })` into protected startup try/catch.
- Added top-level bootstrap fatal catch:
  - logs `auth.bootstrap.fatal`
  - best-effort clears login-progress markers
  - marks auth init error
  - dispatches unauthenticated session
  - calls `renderApp()` for recoverable UI rendering

### 2) Auth/route matrix startup recoverability scenario
- File: `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
- Added storage guard injection helper to simulate storage remove failures.
- Added matrix case:
  - startup under `sessionStorage.removeItem` failure must still reach recoverable terminal state (login UI or keycloak auth endpoint).

### 3) Prebuilt sync stale detection for dirty worktree
- File: `scripts/sync-prebuilt-frontend-if-needed.sh`
- Added `has_frontend_uncommitted_changes` check over frontend source/build-relevant files.
- Enhanced stale detector reason model (`PREBUILT_STALE_REASON`):
  - `missing_manifest`
  - `dirty_worktree`
  - `committed_source_newer_than_build`
  - `up_to_date`
- In `auto`, dirty frontend worktree is treated as stale and triggers rebuild.
- Logs now include stale reason for faster diagnosis.

## Non-Functional Impact
- Improves startup resilience without backend API changes.
- Reduces false-green static smoke runs when local source differs from prebuilt container artifact.
