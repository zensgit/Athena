# Phase 101: Chunk-Load Cache-Busting Reload E2E Hardening

## Date
2026-02-24

## Background
- Phase100 introduced chunk-load failure categorization and cache-busting reload behavior in `AppErrorBoundary`.
- We still needed explicit browser-level regression coverage proving `Reload` appends `_ecm_reload` and returns to a usable login page.

## Goals
1. Add a deterministic mocked E2E assertion for cache-busting reload navigation.
2. Keep the scenario in default `phase5-regression` for continuous protection.

## Changes

### 1) Extended chunk-load mocked E2E
- File: `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts`
- Added case:
  - `App error boundary: chunk-load reload uses cache-busting query (mocked)`
- Assertions:
  - fallback is shown after chunk-load-like rejection
  - clicking `Reload` navigates to URL containing `_ecm_reload=<ts>`
  - login page remains visible/usable after reload

### 2) Gate effect
- File: `scripts/phase5-regression.sh` already includes this spec from Phase100.
- Since the spec now contains two tests, mocked regression total count increases from `26` to `27`.

## Impact
- No backend/API change.
- Strengthens browser-level confidence for stale-asset recovery behavior.
