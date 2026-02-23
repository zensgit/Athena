# Phase 89: Integration Preflight Grouped Diagnostics

## Date
2026-02-21

## Background
- Delivery gate integration layer could fail later with noisy errors when dependencies were down.
- Needed a grouped, actionable preflight before running expensive integration stages.

## Goal
1. Add dependency preflight stage to integration layer.
2. Report grouped remediation hints for missing backend/keycloak/ui dependencies.

## Changes

### 1) New integration dependency preflight stage
- File: `scripts/phase5-phase6-delivery-gate.sh`
- Added stage runner:
  - `run_integration_dependency_preflight_stage`
- Checks:
  - backend health (`${ECM_API_URL}/actuator/health`)
  - keycloak discovery (`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration`)
  - UI reachability (`${ECM_UI_URL_FULLSTACK}`)

### 2) Grouped diagnostics output
- On failures:
  - prints failed dependency list
  - prints deduplicated remediation hints
- Integration stages are skipped early if dependency preflight fails.

### 3) Phase70 script diagnostic clarity
- File: `scripts/phase70-auth-route-matrix-smoke.sh`
- Added `check_endpoint` helper with explicit target + hint messages for:
  - backend health
  - keycloak discovery
  - UI reachability

## Impact
- Faster failure classification in local integration runs.
- Reduced noise from downstream stage failures when prerequisites are unavailable.
