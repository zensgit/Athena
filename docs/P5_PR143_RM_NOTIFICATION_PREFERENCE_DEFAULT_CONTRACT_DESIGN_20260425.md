# P5 PR-143 RM Notification Preference Default Contract Design

## Goal

Align documentation and frontend regression coverage with the shipped RM notification preference defaults.

## Problem

The integration verification document stated that `notifyOnSuccess` defaults to `false`, but the shipped contract is default-on for both success and failure inbox notifications:

- backend preference lookup falls back to `true` when no preference exists
- frontend default state uses `true` for both toggles
- PR-126 design explicitly preserves default-on behavior for both alerts

The incorrect document statement could cause the next executor to misread expected behavior or write the wrong closeout criteria.

## Change

- Correct the integration verification document to state `notifyOnSuccess` defaults to `true`.
- Add a focused `RecordsManagementPage.test.tsx` test proving that missing preference values render both success and failure notification toggles as enabled.

## Boundaries

- no runtime code changed
- no backend endpoint changed
- no E2E gate scope changed
- no acceptance status promoted
