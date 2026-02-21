# Startup Parallel Execution Report

## Date
2026-02-21

## Scope
- Continue after startup-stability 7-day closeout with parallel-track hardening.
- Objective: add deterministic mocked coverage for auth boot watchdog recovery path and keep gate/regression stable.

## Parallel Task Split

1. Track A: Auth boot watchdog recovery determinism
- `ecm-frontend/src/index.tsx`
  - Added E2E boot override hooks:
    - `ecm_e2e_force_auth_boot_hang`
    - `ecm_e2e_auth_boot_watchdog_ms`
  - Override scope: webdriver runtime or localhost runtime.
  - `Continue to login` now forces URL to `/login` before rendering app, making terminal recovery deterministic.
  - Boot override flags are cleaned on continue-to-login recovery.

2. Track B: Mocked regression coverage expansion
- Added `ecm-frontend/e2e/auth-boot-watchdog-recovery.mock.spec.ts`
  - Forces auth boot hang via local storage hook.
  - Verifies watchdog alert/actions.
  - Verifies continue-to-login reaches login page and timeout guidance is visible.
- Integrated into mocked gate:
  - `scripts/phase5-regression.sh` now includes the new spec.

3. Track C: Regression stabilization continuity
- Confirmed prior startup timeout-budget changes remain compatible with watchdog mocked flows.
- Kept `auth-route-recovery.matrix` and `phase70` startup matrix green.

## Delivered Outcome
- New startup recovery scenario is now part of default mocked regression gate.
- Continue-to-login behavior is deterministic and operator-friendly.
- Startup regression baselines remain green after expansion.
