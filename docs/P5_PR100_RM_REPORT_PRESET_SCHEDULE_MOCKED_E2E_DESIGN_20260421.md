# P5 PR-100 RM Report Preset Schedule Mocked E2E

## Date
2026-04-21

## Scope

Add one real Playwright mocked end-to-end coverage slice for the shipped preset scheduled-delivery chain:

- `PR-95` backend scheduled delivery foundation
- `PR-96` frontend service layer
- `PR-97` schedule dialog component
- `PR-98` page wiring
- `PR-99` UI hardening

This slice does **not** add a new runtime endpoint or a new UI surface.
It adds one browser-level mocked spec that proves the existing chain works from the operator surface on `RecordsManagementPage`.

## Why now

The preset delivery chain had controller/service tests and Jest coverage, but it still lacked one browser-level proof that:

1. a CSV-capable preset exposes `Schedule`
2. a summary-only preset stays audit-only
3. opening the dialog loads schedule state and execution history
4. saving schedule config persists and refreshes the authoritative dialog state
5. manual delivery refreshes the dialog and surfaces the latest execution entry

Without that, `PR-95` through `PR-99` were covered in pieces, but not as one reachable operator flow.

## Included

### 1. New mocked Playwright spec

File:
- `ecm-frontend/e2e/rm-report-preset-schedule.mock.spec.ts`

Coverage:
- seeds bypass login
- mocks the admin RM page bootstrap endpoints needed by `RecordsManagementPage`
- provides one CSV-capable preset and one summary-only preset
- mocks schedule status, schedule update, manual delivery, and recent execution history
- asserts:
  - CSV-capable preset row shows `Schedule` and `Export CSV`
  - summary-only preset row keeps `Apply to audit` only
  - dialog opens and loads schedule/history
  - `Save schedule` sends trimmed backend payload
  - `Deliver now` reloads and shows the latest execution artifact

### 2. Current-working-tree execution path

The spec is intended to run against the local dev server on `http://localhost:3000`.

This avoids the stale-build problem seen previously when Playwright auto-detected `http://localhost:5500` and exercised an older UI bundle instead of the current working tree.

## Design Notes

1. The E2E remains mocked at the API layer, not full-stack.
   This keeps the slice deterministic while still validating the real browser/UI chain.
2. The route handler uses an in-memory state model for:
   - preset list
   - schedule status
   - execution history
3. The spec intentionally stays on the smallest useful flow:
   - open schedule
   - save schedule
   - deliver now

It does not add folder-picker, deep navigation, or non-admin coverage.

## Non-goals

- no full-stack scheduler test
- no real backend delivery execution in Playwright
- no email/download-bundle channel coverage
- no new runtime product behavior
