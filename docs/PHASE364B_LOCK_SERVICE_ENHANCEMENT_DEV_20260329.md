# Phase 364B — Lock Service Enhancement

> **Scope**: Lock types, batch/deep lock, suspension, additional info — Alfresco LockService parity
> **Date**: 2026-03-29
> **Backlog ref**: `ATHENA_SURPASS_ALFRESCO_DEVELOPMENT_PLAN_20260329.md` → Sprint 1 Line B

---

## 1. Problem Statement

Athena's lock system stores `locked/lockedBy/lockedDate/lockLifetime/lockExpiresAt`
on `Node` and provides basic lock/unlock via `NodeService`. Missing vs Alfresco:

| Feature | Alfresco | Athena (before) |
|---------|----------|-----------------|
| Lock types (WRITE/READ_ONLY/NODE) | 3 types | 1 implicit |
| Recursive (deep) locking | `lockChildren` param | None |
| Batch lock/unlock | `Collection<NodeRef>` | None |
| Lock metadata | `additionalInfo` | None |
| Lock suspension | `suspendLocks()/enableLocks()` | None |
| `checkForLock()` validation | Throws if locked | Inline check only |
| `isLockedAndReadOnly()` | Yes | None |
| Write semantics per type | Type-aware | Owner-only |

## 2. Design Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | New `LockType` enum: `WRITE_LOCK`, `READ_ONLY_LOCK`, `NODE_LOCK` | Matches Alfresco exactly |
| 2 | New dedicated `LockService` class | SRP — avoids bloating `NodeService`; existing methods unchanged |
| 3 | Lock suspension via `ThreadLocal` | Transaction-scoped; matches Alfresco pattern |
| 4 | `Node.isWriteAllowed(user, now)` method | Encapsulates type-aware write semantics |
| 5 | New endpoints use `-typed` / `-deep` / `batch-` prefix | Backward-compatible with existing `/lock` `/unlock` |
| 6 | `additionalInfo` as varchar(1024) | Sufficient for metadata like "Editing in Collabora" |

## 3. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `entity/LockType.java` | Enum: WRITE_LOCK, READ_ONLY_LOCK, NODE_LOCK |
| `service/LockService.java` | Full lock lifecycle: typed lock, batch, deep, suspension, checkForLock |
| `db/changelog/changes/039-add-lock-type-columns.xml` | Migration: lock_type, lock_additional_info, lock_deep |
| `test/service/LockServiceTest.java` | 31 focused tests across 8 nested classes |

### Modified Files

| File | Change |
|------|--------|
| `entity/Node.java` | +`lockType`, +`lockAdditionalInfo`, +`lockDeep` fields; overloaded `applyLock()`; `clearLock()` resets all; +`isWriteAllowed()`, +`describeActiveLock()` enhanced |
| `dto/LockInfoDto.java` | +`lockType`, +`additionalInfo`, +`lockDeep` fields |
| `service/NodeService.java` | `getLockInfo()` updated for new LockInfoDto shape |
| `controller/NodeController.java` | +`LockService` injection, +4 new endpoints |
| `db/changelog/db.changelog-master.xml` | Include `039-*` |
| `frontend/src/types/index.ts` | +`LockType` type, +`lockType`, +`additionalInfo`, +`lockDeep` on LockInfo |
| 3 existing test files | Constructor param fix for `LockService` |

### NOT Modified

All files in `com.ecm.core.preview.*`, `com.ecm.core.search.*`, and rendition files untouched.

## 4. New Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/nodes/{id}/lock-typed` | Lock with type/lifetime/deep/additionalInfo |
| POST | `/api/nodes/{id}/unlock-deep` | Unlock with recursive option |
| POST | `/api/nodes/batch-lock` | Batch lock multiple nodes |
| POST | `/api/nodes/batch-unlock` | Batch unlock multiple nodes |

## 5. Entity Schema

```sql
ALTER TABLE nodes ADD COLUMN lock_type varchar(32);
ALTER TABLE nodes ADD COLUMN lock_additional_info varchar(1024);
ALTER TABLE nodes ADD COLUMN lock_deep boolean NOT NULL DEFAULT false;
CREATE INDEX idx_nodes_lock_type ON nodes (lock_type);
```

## 6. LockService API

```java
// Lock variants
void lock(UUID nodeId, LockType type);
void lock(UUID nodeId, LockType type, int timeToExpireSeconds);
void lock(UUID nodeId, LockType type, int seconds, LockLifetime lifetime);
void lock(UUID nodeId, LockType type, int seconds, LockLifetime lifetime, boolean lockChildren);
void lock(UUID nodeId, LockType type, LockLifetime lifetime, Integer seconds, boolean deep, String info);

// Batch
void batchLock(Collection<UUID> nodeIds, LockType type, int seconds);
void batchUnlock(Collection<UUID> nodeIds);

// Unlock
void unlock(UUID nodeId);
void unlock(UUID nodeId, boolean unlockChildren);
void unlock(UUID nodeId, boolean unlockChildren, boolean allowCheckedOut);

// Queries
LockStatus getLockStatus(UUID nodeId);
LockInfoDto getLockInfo(UUID nodeId);
boolean isLocked(UUID nodeId);
boolean isLockedAndReadOnly(UUID nodeId);
void checkForLock(UUID nodeId);
String getAdditionalInfo(UUID nodeId);

// Suspension
void suspendLocks();
void enableLocks();
boolean areLocksSuspended();
```

## 7. Lock Type Semantics

| Type | Owner can write | Others can write | Add children allowed |
|------|:-:|:-:|:-:|
| WRITE_LOCK | Yes | No | Yes |
| READ_ONLY_LOCK | No | No | No |
| NODE_LOCK | No | No | Yes |

Enforced via `Node.isWriteAllowed(username, now)`.

## 8. Backward Compatibility

- Existing `/lock` and `/unlock` endpoints unchanged
- Existing `NodeService.lockNode()` / `unlockNode()` unchanged
- `applyLock(user, now, lifetime, expiresAt)` still works — defaults to WRITE_LOCK
- LockInfoDto has 3 new fields at the end — JSON output is additive
