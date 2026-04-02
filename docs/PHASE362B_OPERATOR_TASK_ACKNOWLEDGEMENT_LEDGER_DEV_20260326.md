# Phase 362B: Operator Task Acknowledgement Ledger

## Goal

Close the remaining operator-detail gap in Athena's shared async governance surface by adding a persistent acknowledgement ledger and wiring it into the recent async task feed.

This phase continues the async control plane mainline after:

- `Phase360`: provider registry
- `Phase361/361B`: shared lifecycle feed and action contract
- `Phase362/362A`: admin task center and batch download preflight

## Why This Matters

Paperless-ngx persists task acknowledgement state on `PaperlessTask.acknowledged` and exposes an acknowledge action/filter so operators can clear noise without deleting task history. Alfresco is stronger on download/rendition semantics, but it does not provide a comparable cross-domain operator ledger for recent async work.

Athena already had:

- cross-domain async governance overview
- shared recent-task lifecycle list
- shared cancel / cleanup / download affordances

What it still lacked was a durable, per-operator way to hide resolved noise and restore it later. Without that, the recent-task panel stayed more like a log than an operator queue.

## Backend Changes

### Persistent acknowledgement ledger

Added a dedicated acknowledgement table and entity:

- `ecm-core/src/main/java/com/ecm/core/entity/AsyncTaskAcknowledgement.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AsyncTaskAcknowledgementRepository.java`
- `ecm-core/src/main/resources/db/changelog/changes/032-add-async-task-acknowledgements.xml`

The ledger stores:

- `user_id`
- `domain_key`
- `task_id`
- `task_status`
- `task_fingerprint`
- `task_timestamp`
- `acknowledged_at`

The uniqueness boundary is `user_id + task_fingerprint`, which keeps acknowledgement scoped to the current operator while allowing the same task id to reappear later with a different lifecycle fingerprint.

### Shared lifecycle contract extension

Extended `AsyncTaskStatusSnapshot` so every task can carry:

- `fingerprint`
- `acknowledged`
- `acknowledgedAt`

The acknowledgement state lives on the shared contract, not in a domain-specific controller DTO, so all async domains keep converging on the same operator-facing model.

### Shared acknowledgement service

`ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskAcknowledgementService.java` now owns:

- task fingerprint derivation
- lookup of current user's acknowledgement rows
- acknowledge / unacknowledge persistence
- projection of acknowledgement metadata onto lifecycle snapshots
- hide-vs-show filtering for operator views

### Shared lifecycle service integration

`ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleService.java` now:

- applies acknowledgement metadata to merged lifecycle items
- supports `includeAcknowledged`
- exposes `findRecentTask(...)` so controller actions can resolve the canonical lifecycle snapshot before writing acknowledgements

### Analytics API additions

`ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java` now supports:

- `GET /api/v1/analytics/async-governance/tasks?includeAcknowledged=true|false`
- `POST /api/v1/analytics/async-governance/tasks/acknowledge`
- `POST /api/v1/analytics/async-governance/tasks/unacknowledge`

Recent async task items now return:

- `fingerprint`
- `acknowledged`
- `acknowledgedAt`

## Frontend Changes

`ecm-frontend/src/pages/AdminDashboard.tsx` now treats the recent async task surface as an operator queue instead of a passive feed:

- added `Show acknowledged` toggle
- default view hides acknowledged tasks
- terminal tasks can be acknowledged
- acknowledged tasks can be restored when the toggle is enabled
- acknowledged rows show explicit state and timestamp

This keeps the operator workflow tight:

1. handle terminal task
2. acknowledge it to clear noise
3. restore it later if follow-up is needed

## Benchmark Impact

This phase is one of the clearest places where Athena can exceed the benchmark mix on operator detail:

- vs Alfresco: Athena now has a cross-domain operator queue with per-user acknowledgement semantics, not just per-feature task/status endpoints
- vs Paperless: Athena applies acknowledgement semantics across `audit/search/preview/ops/batchDownload`, not only a single Celery-style task ledger

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskAcknowledgementService.java`
- `ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleService.java`
- `ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskStatusSnapshot.java`
- `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- `ecm-core/src/main/java/com/ecm/core/entity/AsyncTaskAcknowledgement.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AsyncTaskAcknowledgementRepository.java`
- `ecm-core/src/main/resources/db/changelog/changes/032-add-async-task-acknowledgements.xml`
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`
- `ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskAcknowledgementServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskLifecycleServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerSecurityTest.java`
- `ecm-frontend/src/pages/AdminDashboard.tsx`

## Next Step

The next logical step is `Phase363`: move Athena from preview-field semantics to a true rendition resource model so the same operator-detail improvements can apply to rendition state, retry, invalidation, and lifecycle audit.
