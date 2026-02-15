# Phase 5/6 Delivery Gate (One-Command) - Development

## Date
2026-02-15

## Goal
Provide one deterministic command that executes the highest-signal Phase 5 + Phase 6 verification chain:
- mocked regression gate (no backend dependency)
- full-stack admin smoke
- Phase 6 mail automation integration smoke
- Phase 5 Day 6 search integration smoke (spellcheck + Save Search)
- P1 smoke

## Implementation
- Added `scripts/phase5-phase6-delivery-gate.sh`.
- Script orchestration order:
  1. `scripts/phase5-regression.sh`
  2. `scripts/phase5-fullstack-smoke.sh`
  3. `scripts/phase6-mail-automation-integration-smoke.sh`
  4. `scripts/phase5-search-suggestions-integration-smoke.sh`
  5. `ecm-frontend/e2e/p1-smoke.spec.ts`

## Environment Inputs
- `ECM_UI_URL_MOCKED` (default `http://localhost:5500`)
- `ECM_UI_URL_FULLSTACK` (default `http://localhost`)
- `ECM_API_URL` (default `http://localhost:7700`)
- `KEYCLOAK_URL` (default `http://localhost:8180`)
- `KEYCLOAK_REALM` (default `ecm`)
- `ECM_E2E_USERNAME` / `ECM_E2E_PASSWORD` (default `admin` / `admin`)
- `PW_PROJECT` / `PW_WORKERS` (default `chromium` / `1`)

## Why This Helps
- Reduces manual ordering mistakes during local delivery checks.
- Keeps mocked coverage and real-backend coverage in one repeatable gate.
- Makes Phase 5/6 handoff verification auditable with a single command.
