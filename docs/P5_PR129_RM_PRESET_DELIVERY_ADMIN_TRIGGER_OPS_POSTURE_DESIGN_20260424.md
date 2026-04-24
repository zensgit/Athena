# P5 PR-129 RM Preset Delivery Admin Trigger Ops Posture Design

## Goal

Make `POST /api/v1/records/report-presets/run-scheduled-deliveries` explicit as an admin/ops trigger instead of leaving it as an ambiguous full-stack smoke helper.

## Existing Guard

`RmReportPresetController` is class-level guarded:

```java
@PreAuthorize("hasRole('ADMIN')")
```

That matches the local RM admin surface pattern and the existing disposition schedule trigger pattern.

## Changes

- update the OpenAPI summary to name the endpoint an admin ops trigger
- audit each explicit trigger call from `runScheduledDeliveriesNow()`
- include actor, `processedCount`, and `generatedAt` in the audit details
- add a Spring Security MVC test for unauthenticated, non-admin, and admin access

## Audit Contract

Event type:

```text
RM_REPORT_PRESET_SCHEDULED_DELIVERIES_TRIGGERED
```

Node name:

```text
rm-report-preset-scheduled-deliveries
```

Actor:

- current authenticated user
- fallback to `system` only when no current user is available

## Non-Goals

- no endpoint rename
- no response shape change
- no async job queue
- no rate limiter change
- no table or migration change
