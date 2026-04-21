# P5 PR-101 RM Report Preset Schedule Full-Stack Smoke

## Date
2026-04-21

## Scope

Add one real Playwright full-stack/admin smoke for the shipped preset scheduled-delivery chain:

- `PR-95` backend scheduled delivery foundation
- `PR-96` frontend service layer
- `PR-97` schedule dialog component
- `PR-98` page wiring
- `PR-99` UI hardening
- `PR-100` mocked browser coverage

This slice does **not** add a new backend endpoint or a new UI surface.
It adds one non-mocked browser flow that exercises the current working tree frontend against a current-code backend stack.

## Why now

By `PR-100`, the preset scheduled-delivery chain had:

- controller/service coverage
- Jest coverage
- one mocked browser path

What was still missing was one real operator smoke proving that:

1. the running backend actually exposes `schedule / deliver / executions`
2. the current frontend can open the RM preset schedule dialog against a real stack
3. saving schedule config persists through the backend
4. `Deliver now` creates a real execution ledger entry and a real CSV artifact in the target folder

Without that, the scheduled-delivery chain was still proven only in pieces or behind mocked API seams.

## Included

### 1. New full-stack Playwright spec

File:
- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`

Coverage:
- waits for backend readiness
- obtains a real Keycloak token
- creates a real CSV-deliverable preset through the shipped preset API
- creates a real delivery target folder
- opens `RecordsManagementPage`
- opens `Schedule Delivery`
- saves schedule config through the real backend
- triggers `Deliver now`
- polls the execution ledger through the real backend
- verifies the delivered CSV exists in the target folder
- cleans up the preset and folder

### 2. Harder readiness semantics

File:
- `ecm-frontend/e2e/helpers/api.ts`

`waitForApiReady(...)` now requires `/actuator/health` to return `status == UP`.

This avoids treating `OUT_OF_SERVICE` or `UNKNOWN` as “ready”.

### 3. Hard gate instead of conditional skip

The spec no longer skips when `GET /records/report-presets/{id}/schedule` returns `404`.

Now that the running backend can be rebuilt from the current working tree, missing schedule routes are a real failure, not a condition to silently skip.

## Design Notes

1. The smoke intentionally stays on the smallest real operator path:
   - create preset
   - open schedule dialog
   - save schedule
   - deliver now
   - verify execution ledger + delivered file
2. The preset kind is locked to `ACTIVITY_FAMILY_REPORT`, because scheduled delivery is currently CSV-only and limited to report kinds.
3. The spec runs against:
   - current working tree frontend on `http://127.0.0.1:3000`
   - rebuilt backend container on `http://127.0.0.1:7700`

## Non-goals

- no cron-driven scheduler wait/assertion
- no summary-only preset scheduling
- no email/download-bundle channel
- no new runtime product behavior
