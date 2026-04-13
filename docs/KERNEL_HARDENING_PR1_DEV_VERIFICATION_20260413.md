# Kernel Hardening PR-1: Content Reference Ledger — Development & Verification

## Date
2026-04-13

## Scope
PR-1 covers **P0A-1** (Content Reference Ledger) and **P0A-2** (Backfill existing owners).

## Problem Statement

Athena's `ContentService.isContentReferenced()` only checks active `Document.contentId` references — it does not check `Version.contentId`. When a version is deleted, `VersionService.deleteVersion()` calls `contentService.deleteContent()` which may physically delete a binary still referenced by another version, working copy, or the document itself.

```
ContentService.java:301  isContentReferenced() — only queries documentRepository
VersionService.java:249  deleteVersion() — calls deleteContent() after removing version row
```

## Solution

Introduce an authoritative binary ownership ledger (`content_references` table) that tracks every entity holding a reference to a content binary. Physical deletion is deferred to a scheduled orphan cleanup that runs only when zero active references remain.

## Files Created

| File | Purpose |
|------|---------|
| `ecm-core/src/main/java/com/ecm/core/entity/ContentReference.java` | JPA entity: binary ownership record |
| `ecm-core/src/main/java/com/ecm/core/repository/ContentReferenceRepository.java` | Repository with orphan detection, deactivation, and purge queries |
| `ecm-core/src/main/java/com/ecm/core/service/ContentReferenceService.java` | Service: attach/detach/hasActiveReferences + scheduled orphan cleanup |
| `ecm-core/src/main/resources/db/changelog/changes/072-create-content-reference-ledger.xml` | Liquibase: create `content_references` table with indexes |
| `ecm-core/src/main/resources/db/changelog/changes/073-backfill-content-references.xml` | Liquibase: backfill from documents, working copies, and versions |
| `ecm-core/src/test/java/com/ecm/core/service/ContentReferenceServiceTest.java` | Unit tests: 13 test cases |

## Files Modified

| File | Change |
|------|--------|
| `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` | Added 072 and 073 includes |

## Entity Design

```java
@Entity
@Table(name = "content_references")
public class ContentReference {
    UUID id;                    // PK
    String contentId;           // binary storage ID
    OwnerType ownerType;        // DOCUMENT, VERSION, WORKING_COPY, RENDITION
    UUID ownerId;               // FK to owning entity
    boolean active;             // false = detached, pending cleanup
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

**Constraints:**
- Unique: `(content_id, owner_type, owner_id)` — one reference per owner per binary
- Indexes: `(content_id, active)` for orphan queries; `(owner_type, owner_id)` for entity lookups

## Service API

| Method | Behavior |
|--------|----------|
| `attach(contentId, ownerType, ownerId)` | Create or reactivate reference. Idempotent. |
| `detach(contentId, ownerType, ownerId)` | Mark reference inactive. Does NOT delete binary. |
| `hasActiveReferences(contentId)` | Check if any active owner exists. |
| `countActiveReferences(contentId)` | Count active owners. |
| `getActiveReferences(contentId)` | List active owner references. |
| `cleanupOrphanedContent()` | @Scheduled: find orphans, double-check, delete binary, purge records. |

## Feature Flags

| Flag | Default | Purpose |
|------|---------|---------|
| `ecm.storage.reference-ledger.enabled` | `true` | Enable/disable ledger tracking |
| `ecm.storage.orphan-cleanup.enabled` | `false` | Enable orphan cleanup scheduler |
| `ecm.storage.orphan-cleanup.grace-hours` | `24` | Minimum hours before orphan deletion |
| `ecm.storage.orphan-cleanup.cron` | `0 0 3 * * *` | Cleanup schedule (daily 3 AM) |

**Important:** `orphan-cleanup.enabled` defaults to `false`. Must only be enabled after backfill (073) is verified on the target environment.

## Migration Details

### 072: Content Reference Ledger Table
- Creates `content_references` with UUID PK, unique constraint, and 2 indexes
- Uses `${uuid_type}`, `${uuid_function}`, `${now}` for PostgreSQL compatibility
- Rollback: `DROP TABLE content_references`

### 073: Backfill (3 changesets)
1. **Documents** (non-working-copy): `nodes WHERE node_type='DOCUMENT' AND is_working_copy=false`
2. **Working copies**: `nodes WHERE node_type='DOCUMENT' AND is_working_copy=true` → `owner_type=WORKING_COPY`
3. **Versions**: `versions WHERE content_id IS NOT NULL`

All backfills use `NOT EXISTS` guard for safe re-run. Rollback removes by `owner_type`.

### Validation Queries (post-migration)
```sql
-- Document reference count should match non-null content documents
SELECT COUNT(*) FROM content_references WHERE owner_type = 'DOCUMENT';
SELECT COUNT(*) FROM nodes WHERE node_type = 'DOCUMENT' AND content_id IS NOT NULL 
  AND content_id != '' AND (is_working_copy IS NULL OR is_working_copy = false);

-- Version reference count should match non-null content versions  
SELECT COUNT(*) FROM content_references WHERE owner_type = 'VERSION';
SELECT COUNT(*) FROM versions WHERE content_id IS NOT NULL AND content_id != '';

-- No duplicate references
SELECT content_id, owner_type, owner_id, COUNT(*) 
FROM content_references GROUP BY content_id, owner_type, owner_id HAVING COUNT(*) > 1;
```

## Test Coverage

### Unit Tests (ContentReferenceServiceTest) — 13 Cases

**attach (6 tests):**
- Creates new reference for document owner
- Creates new reference for version owner
- Idempotent when reference already active (no save)
- Reactivates previously deactivated reference
- Returns null when ledger disabled
- Returns null for null/blank content ID

**detach (3 tests):**
- Deactivates reference without touching other owners
- Returns 0 when reference does not exist
- Skips when ledger disabled

**hasActiveReferences (3 tests):**
- Returns true when active references exist
- Returns false when no active references
- Returns false for null content ID

**cleanupOrphanedContent (4 tests):**
- Deletes orphaned content with zero active references
- Skips content that gained new references since query (double-check)
- Does nothing when cleanup disabled
- Does nothing when no orphans found

## What This PR Does NOT Do

- Does **not** modify `ContentService.deleteContent()` — that's PR-2 (P0A-3)
- Does **not** modify `VersionService.deleteVersion()` — that's PR-2 (P0A-3)
- Does **not** modify `CheckOutCheckInService` — that's PR-3 (P0A-4)
- Does **not** enable orphan cleanup by default — must be explicitly enabled after verification

## Regression Notes

- No existing behavior is changed. This PR only adds new code and schema.
- Existing `ContentService.isContentReferenced()` continues to work as before.
- The ledger is additive — it does not replace the existing reference check until PR-2.
- Backfill is safe to re-run (idempotent via `NOT EXISTS` guard).

## Verification Checklist

- [ ] `ContentReferenceServiceTest` — all 13 unit tests pass
- [ ] Docker build succeeds (Maven compile + package)
- [ ] Application starts cleanly with new schema (Liquibase runs 072 + 073)
- [ ] Backfill validation queries return expected counts
- [ ] Orphan cleanup remains disabled by default (verify `ecm.storage.orphan-cleanup.enabled=false`)

## Next Steps

After PR-1 is merged and verified:
1. **PR-2 (P0A-3):** Refactor `VersionService.deleteVersion()` and `ContentService.deleteContent()` to use `ContentReferenceService.detach()` instead of direct physical deletion
2. **PR-3 (P0A-4):** Make `CheckOutCheckInService.checkin()` create a version via `VersionService.createVersion()`
