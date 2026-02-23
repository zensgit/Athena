# Phase 94: Startup Visibility SLA (Mocked Gate)

## Date
2026-02-23

## Background
- Startup resilience now has watchdog/recovery coverage, but lacked explicit first-visible SLA checks in default mocked regression.
- Operators need a lightweight way to observe whether login/browse entry points stay within acceptable visibility latency.

## Goals
1. Add deterministic mocked E2E checks for startup first-visible SLA on:
   - `/login`
   - `/browse/root`
2. Include the checks in default `phase5-regression` gate.
3. Surface SLA samples directly in gate output for quick diagnostics.

## Changes

### 1) New mocked E2E
- File: `ecm-frontend/e2e/startup-visibility-sla.mock.spec.ts`
- Added cases:
  1. `Startup SLA: login route visible under threshold (mocked)`
  2. `Startup SLA: browse root visible under threshold (mocked)`
- Behavior:
  - measure elapsed ms from `page.goto(...)` to first-visible key UI signal.
  - assert elapsed time is below threshold:
    - `ECM_E2E_STARTUP_LOGIN_SLA_MS` (default `12000`)
    - `ECM_E2E_STARTUP_BROWSE_SLA_MS` (default `15000`)
  - emit structured sample logs:
    - `startup_sla:login_visible_ms=...:threshold_ms=...`
    - `startup_sla:browse_visible_ms=...:threshold_ms=...`

### 2) Regression gate integration + summary
- File: `scripts/phase5-regression.sh`
- Added spec:
  - `e2e/startup-visibility-sla.mock.spec.ts`
- Extended timing summary to print startup SLA samples when present:
  - section header: `phase5_regression: startup SLA samples`

## Impact
- No backend/API contract changes.
- Adds early warning capability for startup visibility latency drift in mocked gate.
