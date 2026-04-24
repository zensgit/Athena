# P5 PR-128 RM Preset Delivery Notification Publish Hardening

## Goal

Keep scheduled delivery execution evidence independent from notification inbox publication failures.

## Problem

Scheduled delivery success notification publishing happened inside the successful delivery flow after the execution row was saved.

If the activity/notification chain threw after a successful CSV upload, the delivery method could fall into its generic catch block and persist a second failed execution. That would distort product evidence: delivery succeeded, but notification publication failed.

## Change

- wrap success notification publication in a local `try/catch`
- wrap failure notification publication in a local `try/catch`
- log notification publication failures as warnings
- preserve the original delivery execution status and ledger row

## Tests Added

- `runScheduledDeliveries keeps successful execution when success notification publish fails`
- `runScheduledDeliveries keeps failed execution when failure notification publish fails`

## Verification

### Backend Targeted Test

Command:

```bash
cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=RmReportPresetDeliveryServiceTest
```

Result:

- not executable in this local session
- `ecm-core/mvnw` uses Docker and the local Docker socket is not accessible from the sandbox
- observed error: `permission denied while trying to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock`

### Static Diff Check

Command:

```bash
git diff --check
```

Result:

- passed

## Non-Goals

- no retry queue for notification publishing
- no new notification status field on delivery executions
- no endpoint, table, or migration change
