# P5 PR-126 RM Preset Delivery Notification Preferences Design

## Goal

Add the smallest usable preference control for RM preset delivery inbox alerts so owners can independently mute:

- scheduled delivery success notifications
- scheduled delivery failure notifications

This slice keeps the existing notification transport, inbox surface, and People preferences API.

## Scope

### Backend

- honor two owner-scoped preference keys before creating direct inbox notifications in `RmReportPresetDeliveryService`
- keep current behavior as the default when a preference is missing or unreadable
- do not add a new controller, endpoint, table, or migration

Preference keys:

- `org.athena.rm.reportPreset.delivery.notifyOnSuccess`
- `org.athena.rm.reportPreset.delivery.notifyOnFailure`

### Frontend

- load the two preference values from the existing People preferences API
- render two minimal toggles on `RecordsManagementPage` inside `Scheduled Delivery Health`
- persist toggle changes through the existing single-preference upsert API

## Non-Goals

- global notification center redesign
- email channel
- per-preset notification overrides
- delegated or cross-owner notification policy
- full-stack notification preference browser proof in this slice

## Design Notes

- defaults stay `true` for both success and failure alerts so the shipped `PR-123/124/125` behavior is preserved
- backend preference lookup is authoritative; UI only edits the existing preference map
- if preference parsing or lookup fails, backend falls back to `true` and logs a warning rather than silently suppressing alerts
- the RM page toggle is intentionally local to this workstream instead of expanding the generic People preferences editor

## Files

Backend:

- `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java`
- `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetDeliveryServiceTest.java`

Frontend:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Docs:

- `docs/P5_PR126_RM_PRESET_DELIVERY_NOTIFICATION_PREFERENCES_DESIGN_20260424.md`
- `docs/P5_PR126_RM_PRESET_DELIVERY_NOTIFICATION_PREFERENCES_VERIFICATION_20260424.md`
