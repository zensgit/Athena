# P5 PR112 RM Summary Preset Schedule Mocked E2E Design

## Scope

This slice adds browser-level mocked coverage for the `PR-111` capability:

- summary-only preset kinds now expose `Export CSV`
- summary-only preset kinds now expose `Schedule`
- summary-only preset kinds can open the shipped `Schedule Delivery` dialog
- summary-only preset kinds can save schedule state and trigger manual delivery

Covered kinds:

- `ACTIVITY_FAMILY_HIGHLIGHTS`
- `ACTIVITY_FAMILY_MIX`

## Why This Slice

`PR-111` changed the preset contract, but the existing mocked Playwright spec still locked in the old behavior:

- summary-only preset rows had no `Schedule`
- summary-only preset rows had no `Export CSV`

That meant the browser-level evidence was stale even though unit and integration tests were green.

## Design

### Reuse Existing Mocked Preset Flow

Instead of adding a new spec, extend the shipped mocked preset schedule spec:

- file: `ecm-frontend/e2e/rm-report-preset-schedule.mock.spec.ts`

This keeps coverage in the same operator workflow that already validates:

- preset row actions
- dialog open/load
- schedule save
- manual delivery

### Add Summary-Only Fixtures

The mocked preset list now includes:

- one deliverable family-report preset
- one `ACTIVITY_FAMILY_HIGHLIGHTS` preset
- one `ACTIVITY_FAMILY_MIX` preset

The highlights preset is used for full schedule/export interaction.
The mix preset is used as an additive visibility/assertion check so both summary-only kinds are represented in browser coverage.

### Update Mock State

The dialog now loads immediately for summary-only kinds, so the mock state must also include:

- `scheduleState` for the summary-only preset
- `executionState` for the summary-only preset

### Align to Current UI

The shipped UI no longer uses the old freeform delivery-folder text input.

The mocked E2E now selects the folder through the current `FolderTree` picker behavior by:

- mocking the root folder contents
- exposing one real folder node: `Delivery Target`
- clicking the tree item instead of filling a removed textbox

### CSV Export Verification

The spec now also mocks:

- `GET /api/v1/records/activity-family-report?format=csv`

and asserts that clicking `Export CSV` on the summary-only preset row triggers the family-report CSV route with `format=csv`.

This matches the shipped frontend behavior after `PR-111`, where summary-only preset export reuses family-report CSV semantics.

## Non-goals

- no full-stack/admin smoke extension in this slice
- no new backend endpoint
- no runtime code changes beyond test coverage
- no email delivery coverage

## Expected Outcome

After this slice, the browser-level mocked evidence matches the shipped preset contract:

- summary-only preset rows are no longer treated as audit-only
- summary-only preset schedule/export behavior is covered end-to-end in the mocked RM preset workflow
