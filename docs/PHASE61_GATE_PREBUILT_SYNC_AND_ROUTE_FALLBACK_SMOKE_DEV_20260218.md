# Phase 61: Gate Prebuilt Sync + Route Fallback Smoke - Development

## Date
2026-02-18

## Background
- `phase5-phase6` full-stack stages can run against `http://localhost` (static/prebuilt target).
- When local `ecm-frontend/build` is stale, users can still hit blank-page behavior on unknown routes even though source code already contains fallback fixes.
- `scripts/rebuild-frontend-prebuilt.sh` used `docker compose up -d --build ecm-frontend` and could trigger unnecessary dependent service builds.

## Goal
1. Make full-stack gate safer on static targets by syncing prebuilt frontend when needed.
2. Keep prebuilt refresh fast and scoped to frontend only.
3. Add P1 smoke coverage for unknown-route fallback with stable SPA assertion semantics.

## Design
1. `scripts/phase5-phase6-delivery-gate.sh`
   - Add `ECM_SYNC_PREBUILT_UI` with values:
     - `auto` (default): when full-stack target is local static proxy (`http://localhost`/`http://127.0.0.1`), rebuild prebuilt UI only if stale.
     - `1`: force rebuild prebuilt UI.
     - `0`: skip prebuilt sync.
   - Add stale detector:
     - Compare `ecm-frontend/build/asset-manifest.json` mtime with latest git commit timestamp on frontend source/package files.
   - Run sync check before full-stack stages.
2. `scripts/rebuild-frontend-prebuilt.sh`
   - Default to frontend-only refresh:
     - `docker compose up -d --no-deps --build ecm-frontend`
   - Keep optional full dependency path via `FRONTEND_REBUILD_WITH_DEPS=1`.
3. `ecm-frontend/e2e/p1-smoke.spec.ts`
   - Keep unknown-route fallback scenario.
   - Replace `page.waitForURL(...)` with `expect.poll(() => page.url())` to support SPA same-document URL transitions reliably.

## Files
- `scripts/phase5-phase6-delivery-gate.sh`
- `scripts/rebuild-frontend-prebuilt.sh`
- `ecm-frontend/e2e/p1-smoke.spec.ts`
