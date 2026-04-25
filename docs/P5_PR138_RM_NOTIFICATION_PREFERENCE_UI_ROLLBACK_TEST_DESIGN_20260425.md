# P5 PR-138 RM Notification Preference UI Rollback Test Design

## Goal

Add frontend regression coverage for the Records Management notification preference UI when the success preference update fails.

## Problem

`RecordsManagementPage` optimistically updates the success/failure inbox notification toggles before calling `peopleService.setPreference`. The runtime code already rolls the toggle back and shows an error when the service call fails, but the test suite only covered the successful update path.

That left a UI-state gap: a future change could leave the toggle visually changed even though the backend rejected the preference update.

## Change

`RecordsManagementPage.test.tsx` now includes a success-toggle failure-path test that verifies:

- initial preference state loads as enabled
- clicking `Success inbox notifications` calls `peopleService.setPreference`
- rejected update rolls the switch back to the previous value
- warning copy is rendered in the health card
- error toast is emitted

`PR-140` adds the corresponding failure-toggle mirror test.

## Boundaries

- no runtime code changed
- no backend endpoint changed
- no E2E gate scope changed
- no product behavior changed
