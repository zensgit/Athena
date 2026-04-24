# P5 PR-125 RM Preset Delivery Success Notification Full-Stack Smoke Design

## Scope

`PR-125` extends the new preset-delivery inbox alerting lane from failure-only to **scheduled success** notifications.

This slice includes:

- direct owner inbox notification for successful scheduled deliveries
- existing admin trigger reuse for due scheduled deliveries
- a real browser proof through `/notifications`

This slice does **not** add:

- notification preferences
- email delivery
- new inbox APIs
- new tables or migrations

## Design

### Backend

`RmReportPresetDeliveryService` now posts a direct notification activity when a **scheduled** delivery succeeds.

Activity shape:

- `activityType = rm.report_preset.delivery.succeeded`
- `userId = system`
- `siteId = null`
- `nodeId = documentId` when available, else `targetFolderId`
- `nodeName = filename`

Summary payload includes:

- `presetId`
- `presetName`
- `presetKind`
- `triggerType`
- `filename`
- `targetFolderId`
- `documentId`
- `message`
- `executionId`
- `status`

### Frontend

Existing notification/activity consumers were extended to understand:

- label: `Scheduled Delivery Succeeded`
- summary: readable preset + trigger + filename copy
- links:
  - `Open Records Management`
  - existing `Open Node` drilldown to the delivered document

## Why This Slice

After `PR-124`, failed scheduled deliveries already reached the inbox with real-stack proof. The next smallest useful increment was to let owners also confirm successful scheduled deliveries without reopening the RM admin page first.

## Boundaries

- only **scheduled** successes notify; manual `Deliver now` still does not
- the inbox visibility rule is unchanged: notification visibility still depends on the referenced `nodeId` being visible under the current tenant scope
- the notification does not deep-link to a specific ledger row; it links to RM and the delivered node
