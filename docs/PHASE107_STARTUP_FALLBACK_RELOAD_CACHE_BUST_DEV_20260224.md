# Phase 107: Startup Fallback Reload Cache-Bust Hardening

## Date
2026-02-24

## Background
- Startup fallback overlay already provided `Reload`, but used plain `window.location.reload()`.
- In stale asset or cache-resident startup failures, plain reload may repeat the same failure.

## Goals
1. Apply cache-busting semantics to startup fallback reload path.
2. Add mocked E2E coverage for startup fallback reload behavior.
3. Extend recovery guard expected marker set accordingly.

## Changes

### 1) Startup fallback reload cache-bust
- `ecm-frontend/public/index.html`
  - added `buildCacheBustReloadUrl()` helper in bootstrap fallback script.
  - `Reload` action now:
    - appends `_ecm_reload=<timestamp>` to current URL when possible.
    - falls back to `window.location.reload()` only when URL rewrite is unavailable.

### 2) Mocked E2E coverage
- `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
  - added test:
    - `Startup fallback: reload uses cache-busting query and restores login shell`
  - asserts:
    - URL contains `_ecm_reload=<ts>`
    - login shell text is visible after reload
  - emits marker:
    - `recovery_event:startup_fallback_reload_cache_bust`

### 3) Recovery guard expected event expansion
- `scripts/phase5-regression.sh`
  - expected recovery events now include:
    - `startup_fallback_reload_cache_bust`

## Impact
- No backend/API change.
- Improves startup recovery reliability under stale bundle/cache conditions and keeps gate telemetry aligned with new recovery path.
