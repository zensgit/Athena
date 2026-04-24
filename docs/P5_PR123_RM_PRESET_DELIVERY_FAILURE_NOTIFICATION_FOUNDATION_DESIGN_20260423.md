# P5 PR-123 RM Preset Delivery Failure Notification Foundation Design

## Scope

`PR-123` adds the smallest new post-`PR-122` delivery capability: owner-scoped inbox notifications for **failed scheduled RM preset deliveries**.

It does **not** add:

- email delivery
- success notifications
- new notification pages or APIs
- new tables or migrations

## Problem

`PR-95` through `PR-122` already shipped schedule, delivery, ledger, telemetry, and health drilldowns, but the workstream still had no proactive owner-facing alert when a scheduled delivery failed. Operators had to discover failures by revisiting `Records Management`.

## Design

### Backend

- `NotificationInboxService` now exposes `createDirectNotification(userId, activity)` for owner-scoped inbox delivery without follower fan-out.
- `ActivityService` now exposes `postDirectNotificationActivity(...)`, which persists an `Activity` row and immediately inserts a `Notification` for a single recipient.
- `RmReportPresetDeliveryService` now emits a direct notification only when a **scheduled** preset delivery fails.

### Activity shape

The failure alert is stored as an activity with:

- `activityType = rm.report_preset.delivery.failed`
- `userId = system`
- `nodeId = targetFolderId`
- `siteId = null`
- summary fields containing:
  - `presetId`
  - `presetName`
  - `presetKind`
  - `triggerType`
  - `filename`
  - `targetFolderId`
  - `message`
  - `executionId`
  - `status`

Using `nodeId = targetFolderId` keeps the notification visible in tenant-scoped inbox filtering, because tenant visibility is derived from the referenced node path.

### Frontend consumption

No new page was added. Existing notification and activity renderers were extended to understand `rm.report_preset.delivery.failed`:

- label: `Scheduled Delivery Failed`
- summary: readable preset + trigger + message copy
- link target: `Open Records Management`
- existing node drilldown remains available when `nodeId` is present

## Runtime Boundaries

- Only **scheduled** failures notify the owner.
- Manual `Deliver now` failures still surface through existing page/UI feedback and ledger evidence, but do not create inbox alerts.
- The activity is visible in inbox/global/node feeds when the referenced folder node is visible.
- Site-only activity feeds do not infer site membership from `nodeId`, so they do not gain a new site-scoped alert surface from this slice.

## Why This Slice

This is the smallest meaningful follow-up after the preset delivery milestone closeout:

- reuses the shipped notification infrastructure
- avoids inventing an email channel prematurely
- adds an actual alerting surface for the most operationally important case
- preserves the current evidence model: inbox alert -> RM page -> ledger/document evidence
