# P5 PR-124 RM Preset Delivery Failure Notification Full-Stack Smoke Design

## Scope

`PR-124` turns the new `PR-123` failed-delivery inbox alert into real-stack evidence.

To make that practical without waiting for the scheduler cron window, this slice also adds one small admin-only trigger:

- `POST /api/v1/records/report-presets/run-scheduled-deliveries`

That endpoint reuses the shipped scheduled runner and returns only a minimal execution summary.

## Why This Slice Exists

`PR-123` proved the direct notification foundation with backend/unit and frontend formatter tests, but it still lacked a browser-level proof that a **real scheduled failure** reaches `/notifications`.

The existing `Deliver now` path was not enough because direct inbox notifications are intentionally emitted only for `scheduledRun=true`.

## Design

### Backend

- `RmReportPresetDeliveryService` now exposes `runScheduledDeliveriesNow()` and internally factors the due-preset loop into a reusable method.
- `RmReportPresetController` now exposes `POST /api/v1/records/report-presets/run-scheduled-deliveries`.
- No new table, queue, or migration was introduced.

### Full-stack test strategy

The smoke does not use a fake UUID for the failing target, because an out-of-scope or non-existent node would weaken notification visibility semantics under tenant-scoped filtering.

Instead it:

1. creates a real folder
2. uploads a real document into that folder
3. configures scheduled delivery using that **document id** as `deliveryFolderId`
4. forces `next_run_at` into the past
5. calls the new admin trigger endpoint
6. verifies the owner inbox shows the failed scheduled delivery notification
7. verifies `Open Records Management` navigates back to the RM admin page

Using a real document node keeps `nodeId = targetFolderId` visible in tenant-scoped inbox filtering while still causing delivery to fail because the target node is not a folder.

## Boundaries

- still no email channel
- still no success notification slice
- still no new notification UI surface beyond `/notifications`
- the admin trigger is intentionally small and synchronous; it is not a job dashboard or scheduler orchestration API
