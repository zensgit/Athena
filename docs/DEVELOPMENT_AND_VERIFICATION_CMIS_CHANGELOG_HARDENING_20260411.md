# Development And Verification: CMIS Change Log Hardening

Date: 2026-04-11

## Scope

This batch continued the CMIS hardening line and closed the remaining change-log risks that were left after the prior CMIS relationship/versioned-objectId fix:

1. `CmisChangeLogService` performed per-entry node lookups, creating an avoidable N+1 path.
2. `NODE_DELETED` entries disappeared once the backing node row was no longer available.
3. Hard-deleted events had no durable visibility snapshot, so tenant/ACL-aware filtering could not be reconstructed after deletion.
4. A fully invisible audit slice could force the service to scan far too much history in a single request.

## Implemented Changes

### 1. Delete-event visibility snapshot

- `NodeDeletedEvent` now carries:
  - `nodePath`
  - `readableAuthorities`
- `NodeService.deleteNode(...)` snapshots read authorities before deletion.
- `FolderService.deleteFolder(...)` does the same for folders.

Files:

- `ecm-core/src/main/java/com/ecm/core/event/NodeDeletedEvent.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/service/FolderService.java`

### 2. Audit metadata for delete events

- `AuditService` gained metadata-capable logging.
- `logNodeDeleted(...)` now persists deletion metadata JSON including:
  - `path`
  - `nodeType`
  - `permanent`
  - `readableAuthorities`
- `EcmEventListener.handleNodeDeleted(...)` now passes the deletion snapshot into audit logging.

Files:

- `ecm-core/src/main/java/com/ecm/core/service/AuditService.java`
- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`

### 3. Batch visibility filtering in CMIS change log

- `CmisChangeLogService` now batch-loads candidate nodes with `findAllById(...)` instead of per-entry repository lookups.
- Visibility resolution now has two branches:
  - live/deleted node row present: use node path + `READ` permission
  - hard-deleted row missing: use audit metadata path + readable-authority snapshot
- Admin remains a fallback for missing-node delete events when metadata is incomplete.
- Added a bounded scan limit per request to avoid pathological full-history scans when the caller can see nothing in the current slice.

File:

- `ecm-core/src/main/java/com/ecm/core/cmis/CmisChangeLogService.java`

## Tests Added / Updated

- Reworked `CmisChangeLogServiceTest` to match the new batch-visibility model.
- Covered:
  - stable cursor pagination
  - same-timestamp continuation
  - tenant path filtering
  - hard-delete metadata fallback
  - legacy timestamp-token compatibility
  - stable caller token on empty result

File:

- `ecm-core/src/test/java/com/ecm/core/cmis/CmisChangeLogServiceTest.java`

## Verification

### Compile

Command:

```bash
cd ecm-core
mvn -q -DskipTests compile
```

Result:

- Passed

### Focused CMIS change-log/controller tests

Command:

```bash
cd ecm-core
mvn -q -Dtest=CmisChangeLogServiceTest,CmisBrowserControllerTest,CmisInteropSmokePackTest test
```

Result:

- Passed

### Diff hygiene

Command:

```bash
git diff --check
```

Result:

- Passed

## Notes

- Claude Code CLI was attempted as a parallel read-only reviewer in this batch, but the local CLI session was not authenticated and returned `Not logged in · Please run /login`. All implementation and verification in this batch were completed locally.
- The current change-log hardening is intentionally request-bounded. If a caller has visibility into only a tiny subset of audit history, repeated polling may still be needed to move through long invisible stretches, but a single request will no longer scan unbounded history.
