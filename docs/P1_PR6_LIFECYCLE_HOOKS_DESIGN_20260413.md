# P1 PR-6 Lifecycle Hooks Design

## Date
- 2026-04-13

## Status
- Implemented

## Objective
- Replace scattered service-level rule triggering with one minimal repository lifecycle surface.
- Keep existing audit, indexing, preview, and notification listeners working without a broad Alfresco-style policy rewrite.

## Scope
- Add a shared lifecycle event contract.
- Publish lifecycle events from the first wave of repository write paths.
- Move rule dispatch behind one `AFTER_COMMIT` lifecycle listener.
- Preserve legacy domain events so existing listeners do not break.

## Implemented Design

### Lifecycle Contract
- Added `RepositoryLifecycleAction` as the canonical action enum.
- Added `RepositoryLifecycleEvent` as the shared payload for repository writes.
- Added `RepositoryLifecyclePublisher` as a compatibility publisher that emits:
  - existing Spring application events already consumed by `EcmEventListener`
  - one new lifecycle event for shared downstream dispatch

### Rule Dispatch Consolidation
- Added `RepositoryLifecycleRuleListener`.
- Listener runs with `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- Listener dispatches rules only when `ruleTriggerType` is set.
- Rule dispatch now uses one path:
  - `NODE_UPDATED -> DOCUMENT_UPDATED`
  - `NODE_MOVED -> DOCUMENT_MOVED`
  - `VERSION_CREATED -> VERSION_CREATED`
- `NODE_CHECKED_IN` is emitted as a lifecycle action but does not introduce a second direct rule trigger.

### Converted Write Paths
- `NodeService`
  - `createNode`
  - `updateNode`
  - `moveNode`
  - `deleteNode`
- `FolderService`
  - create/update/delete flows
- `BulkMetadataService`
  - update fan-out now goes through the shared lifecycle publisher
- `VersionService`
  - version creation emits compatibility + lifecycle events
- `CheckOutCheckInService`
  - check-in emits `NODE_CHECKED_IN`
- `SecurityService`
  - permission-change indexing now uses the shared lifecycle publisher

## Compatibility Rules
- `NodeCreatedEvent`, `NodeUpdatedEvent`, `NodeMovedEvent`, `NodeDeletedEvent`, `VersionCreatedEvent`, and `NodePermissionsChangedEvent` remain in use.
- `EcmEventListener` keeps responsibility for:
  - audit
  - indexing
  - preview/OCR enqueue
  - notifications
- `PR-6` does not rewrite comment, tag, or category event flows.

## Non-Goals
- No full policy registry.
- No lifecycle persistence table.
- No rewrite of domain-specific listeners outside the first-wave repository write paths.

## Files
- `ecm-core/src/main/java/com/ecm/core/event/RepositoryLifecycleAction.java`
- `ecm-core/src/main/java/com/ecm/core/event/RepositoryLifecycleEvent.java`
- `ecm-core/src/main/java/com/ecm/core/event/RepositoryLifecyclePublisher.java`
- `ecm-core/src/main/java/com/ecm/core/event/RepositoryLifecycleRuleListener.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/service/FolderService.java`
- `ecm-core/src/main/java/com/ecm/core/service/BulkMetadataService.java`
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`
- `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`

## Exit Conditions
- Repository write paths no longer call rule dispatch directly from service code.
- Compatibility listeners still receive the legacy events they expect.
- Bulk metadata updates do not introduce duplicate rule/index paths.
