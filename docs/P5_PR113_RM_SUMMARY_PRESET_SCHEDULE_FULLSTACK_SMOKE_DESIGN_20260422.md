# P5 PR-113 RM Summary Preset Schedule Full-Stack Smoke Design

## Scope

- add one non-mocked Playwright admin smoke for the shipped summary-only preset contract
- cover `ACTIVITY_FAMILY_HIGHLIGHTS` summary-only preset export and scheduled delivery from the real `Records Management` page
- keep the existing deliverable preset full-stack smoke in the same spec green
- do not add any new backend endpoint, table, migration, or page runtime surface

## Why This Slice

`PR-111` and `PR-112` proved the summary-only preset contract at the unit/integration and mocked-browser layers, but there was still no authoritative full-stack/admin proof on a live frontend + live backend stack.

This slice closes that gap without expanding product scope.

## Implementation

Primary file:

- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`

Main changes:

- factor preset creation through `createPreset(...)` so both deliverable and summary-only full-stack cases share the same setup path
- add `findPresetRow(...)` and keep row lookup scoped to the `Saved RM Report Presets` card
- switch full-stack folder selection to the shipped `FolderTree` picker flow instead of the removed folder-id textbox
- add a second full-stack test for `ACTIVITY_FAMILY_HIGHLIGHTS`
  - create preset with `windowDays: 7`
  - verify row-level `Export CSV`
  - verify `Schedule Delivery` dialog can save cron + folder
  - verify `Deliver now`
  - verify delivered document lands in the chosen folder

## Test Robustness Notes

- row lookup is card-scoped because the page now contains multiple schedule/ledger surfaces with overlapping text
- summary-only export is executed before opening the dialog because the modal marks the background subtree as hidden for accessibility, which makes role-based button lookup behind the dialog unreliable
- authoritative verification uses current-worktree frontend at `:3000` plus rebuilt current-code docker backend at `:7700`

## Non-Goals

- no new runtime product capability
- no new mocked spec
- no email delivery channel
- no CI/workflow change
