# P5 PR-137 RM Notification Preference Service Contract Tests Design

## Goal

Add frontend service-layer regression coverage for the People preference endpoints used by the Records Management notification preference toggles.

## Problem

The RM page test already verifies that preference toggles call `peopleService.setPreference`, and the full-stack notification acceptance tests verify backend preference behavior. The service unit tests covered namespace fetch/export/import, but not the single-preference get/set/delete endpoint construction that the RM page depends on.

That left a small cross-boundary gap: a future URL encoding or payload regression in `peopleService.setPreference` could break the UI while page tests still pass against a mocked service.

## Change

`peopleService.test.ts` now covers:

- `getPreference` URL construction with encoded username
- `setPreference` URL construction and `{ value }` payload
- `deletePreference` URL construction with encoded username and encoded preference path separators

The tests use the existing RM notification preference keys as the concrete contract.

## Boundaries

- no service runtime code changed
- no backend endpoint changed
- no page behavior changed
- no new acceptance gate case added
