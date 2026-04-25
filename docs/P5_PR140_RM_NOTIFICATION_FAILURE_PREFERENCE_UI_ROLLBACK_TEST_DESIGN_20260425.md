# P5 PR-140 RM Notification Failure Preference UI Rollback Test Design

## Goal

Complete page-level rollback coverage for both RM notification preference toggles.

## Problem

`PR-138` covered failed optimistic updates for the success notification toggle. The failure notification toggle uses the same update path but a different preference key:

```text
org.athena.rm.reportPreset.delivery.notifyOnFailure
```

Without a mirror test, a future key mapping regression could affect failure notifications while the success rollback test still passes.

## Change

`RecordsManagementPage.test.tsx` now includes a dedicated failure-toggle rollback test.

It verifies:

- initial failure notification preference loads enabled
- clicking `Failure inbox notifications` calls `peopleService.setPreference`
- rejected update uses the `notifyOnFailure` preference key
- the switch rolls back to the previous checked state
- warning copy and error toast are shown

## Boundaries

- no runtime code changed
- no backend endpoint changed
- no E2E gate scope changed
- no product behavior changed
