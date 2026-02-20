# Phase 74: Delivery Gate Integration Layer - Auth/Route Matrix Stage

## Date
2026-02-20

## Background
- Phase70 added a dedicated `auth-route` matrix smoke script, but it was not yet part of the unified integration delivery gate stages.
- This left a coverage gap where integration gate could pass without executing auth/route terminal-state regression checks.

## Goal
1. Include Phase70 matrix smoke in integration gate default flow.
2. Keep existing layered summary and failure reporting behavior unchanged.
3. Reuse existing full-stack environment variables and prebuilt sync strategy.

## Changes

### 1) New integration stage function
- File: `scripts/phase5-phase6-delivery-gate.sh`
- Added `run_phase70_auth_route_matrix_stage()`:
  - uses `ECM_UI_URL_FULLSTACK`, `ECM_API_URL`, `KEYCLOAK_URL`, `KEYCLOAK_REALM`
  - reuses gate `PW_PROJECT`, `PW_WORKERS`
  - passes through `FULLSTACK_ALLOW_STATIC`
  - sets `ECM_SYNC_PREBUILT_UI=0` to avoid duplicate prebuilt sync

### 2) Stage insertion into integration layer
- File: `scripts/phase5-phase6-delivery-gate.sh`
- Added a `run_stage` call:
  - key: `phase70_auth_route_matrix_smoke`
  - label: `phase70 auth-route matrix smoke`
  - execution order: after search integration smoke and before `p1 smoke`

## Non-Functional Notes
- No frontend/backend runtime behavior changes.
- Scope is limited to regression orchestration and coverage completeness.
