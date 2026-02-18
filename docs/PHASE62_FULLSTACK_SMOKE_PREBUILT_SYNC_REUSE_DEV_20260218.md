# Phase 62: Full-stack Smoke Prebuilt Sync Reuse - Development

## Date
2026-02-18

## Background
- Phase 61 introduced stale-prebuilt auto-sync in `scripts/phase5-phase6-delivery-gate.sh`.
- When engineers run full-stack smoke scripts directly (without the delivery gate), stale prebuilt risk can still reappear.

## Goal
1. Reuse the same prebuilt-sync policy across standalone full-stack smoke scripts.
2. Avoid duplicated sync logic in multiple scripts.
3. Keep delivery gate behavior deterministic (single sync in gate path).

## Design
1. Add shared helper:
   - `scripts/sync-prebuilt-frontend-if-needed.sh`
   - Inputs:
     - positional `target_ui_url` (fallback `ECM_UI_URL`)
     - env `ECM_SYNC_PREBUILT_UI` (`auto` default / `1` force / `0` skip)
   - Behavior:
     - only applies to local static proxy targets (`http://localhost`, `http://127.0.0.1`)
     - detects stale prebuilt by comparing `build/asset-manifest.json` mtime with latest frontend source commit timestamp
     - triggers `scripts/rebuild-frontend-prebuilt.sh` when policy requires
2. Integrate helper into standalone full-stack smoke scripts:
   - `scripts/phase5-fullstack-smoke.sh`
   - `scripts/phase6-mail-automation-integration-smoke.sh`
   - `scripts/phase5-search-suggestions-integration-smoke.sh`
3. Keep delivery gate single-sync:
   - `scripts/phase5-phase6-delivery-gate.sh` still runs one sync after mocked stage
   - child smoke scripts are invoked with `ECM_SYNC_PREBUILT_UI=0` to avoid duplicate sync calls in gate flow

## Files
- `scripts/sync-prebuilt-frontend-if-needed.sh`
- `scripts/phase5-fullstack-smoke.sh`
- `scripts/phase6-mail-automation-integration-smoke.sh`
- `scripts/phase5-search-suggestions-integration-smoke.sh`
- `scripts/phase5-phase6-delivery-gate.sh`
