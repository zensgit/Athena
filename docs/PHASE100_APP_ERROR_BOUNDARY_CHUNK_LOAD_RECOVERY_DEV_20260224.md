# Phase 100: AppErrorBoundary Chunk-Load Recovery Guidance

## Date
2026-02-24

## Background
- Chunk/dynamic-import load failures (`Loading chunk ... failed`, `ChunkLoadError`) can occur after deployment or stale asset cache.
- Prior fallback UI was generic and did not clearly guide operators/users toward asset refresh behavior.

## Goals
1. Detect chunk-load failures as a distinct error category.
2. Show explicit recovery hint for stale/outdated frontend assets.
3. Use cache-busting reload path when user clicks `Reload` on chunk-load fallback.
4. Add mocked E2E + gate coverage for this path.

## Changes

### 1) Chunk-load category and reload strategy
- File: `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
- Added:
  - error category model: `generic | chunk_load`
  - chunk-load pattern detection:
    - `Loading chunk ... failed`
    - `ChunkLoadError`
    - `Failed to fetch dynamically imported module`
    - module-script import failure variants
  - helper: `buildCacheBustReloadUrl(...)` adds `_ecm_reload=<ts>` query param
- Behavior:
  - chunk-load failures now show targeted hint:
    - `Application files may be outdated after an update. Reload to fetch the latest assets.`
  - clicking `Reload` under chunk-load fallback uses `location.assign(cacheBustedUrl)` instead of plain `location.reload()`.

### 2) Unit tests
- File: `ecm-frontend/src/components/layout/AppErrorBoundary.test.tsx`
- Added tests:
  - chunk-load unhandled rejection shows asset-refresh guidance.
  - cache-busting URL builder appends `_ecm_reload`.

### 3) Mocked E2E + gate integration
- File: `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts`
  - scenario: dispatch chunk-load-like unhandled rejection and verify fallback + targeted hint.
- File: `scripts/phase5-regression.sh`
  - add spec to default mocked suite.
  - mocked case count increases from `25` to `26`.

## Impact
- No backend/API changes.
- Improves runtime recovery clarity for stale asset/chunk mismatch incidents.
