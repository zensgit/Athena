# Phase 368A — Persisted Working-Copy Backbone

> **Scope**: Check-Out/Check-In with real working-copy Document nodes
> **Date**: 2026-03-29
> **Backlog ref**: `ATHENA_SURPASS_ALFRESCO_DEVELOPMENT_PLAN_20260329.md` → Sprint 2 Line A

---

## 1. Problem Statement

Athena's current checkout model stores checkout state as denormalized columns on
the **original** Document (`checkoutUser`, `checkoutDate`,
`checkoutBaselineVersionId`). No separate working-copy row exists — the graph
endpoints synthesise a _virtual_ working copy for the UI.

Alfresco creates a **persisted working copy** — a real node in the repository —
on checkout. This enables:

- checking out a document to a **different folder** (e.g. user's home)
- concurrent readers see the original content until checkin
- working-copy content can diverge from the original and be discarded on cancel
- relation queries (`workingCopyOf ↔ original`) become first-class DB joins

## 2. Design Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Working copy is a **real `Document` row** with `is_working_copy=true` | Matches Alfresco model; enables standard node queries |
| 2 | `working_copy_of` UUID FK on `documents` | Single-column join; no new join table needed |
| 3 | Working copy is **soft-deleted** on checkin/cancel | Consistent with existing trash system; enables audit trail |
| 4 | Working copy is **not versioned** (`versioned=false`) | Avoids version clutter; versions belong to the original |
| 5 | New service `CheckOutCheckInService` | Dedicated SRP class; avoids bloating `NodeService` further |
| 6 | New endpoints use `-wc` suffix | Backward-compatible; existing `/checkout` `/checkin` endpoints unchanged |
| 7 | **No modifications** to preview/rendition/search files | Per constraint — those are hot files with pending changes |

## 3. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `service/CheckOutCheckInService.java` | Core working-copy lifecycle: checkout → checkin → cancel |
| `db/changelog/changes/038-add-document-working-copy-columns.xml` | Migration: `working_copy_of`, `is_working_copy` columns + indexes |
| `test/service/CheckOutCheckInServiceTest.java` | 19 focused unit tests covering all branches |
| `test/controller/DocumentControllerWorkingCopyTest.java` | 8 MockMvc tests for new endpoints |

### Modified Files

| File | Change |
|------|--------|
| `entity/Document.java` | +`workingCopyOf` (UUID), +`isWorkingCopy` (boolean), `checkin()` clears both |
| `dto/NodeDto.java` | +`workingCopyOf`, +`isWorkingCopy` fields in record; populated in `from()` |
| `repository/DocumentRepository.java` | +`findWorkingCopyOf()`, +`findWorkingCopiesByUser()` |
| `controller/DocumentController.java` | +`CheckOutCheckInService` injection, +5 new endpoints |
| `db/changelog/db.changelog-master.xml` | Include `038-*` migration |

### NOT Modified (constraint)

All files in `com.ecm.core.preview.*`, `com.ecm.core.search.*`, and
rendition-related files are untouched.

## 4. New Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/documents/{id}/checkout-wc` | Checkout creating persisted working copy |
| POST | `/api/documents/{id}/checkout-wc?destination={folderId}` | Checkout to specified folder |
| POST | `/api/documents/{wcId}/checkin-wc` | Checkin working copy (optional file + keepCheckedOut) |
| POST | `/api/documents/{id}/cancel-checkout-wc` | Cancel checkout (accepts original OR wc ID) |
| GET | `/api/documents/{id}/working-copy` | Get working copy of a checked-out document |
| GET | `/api/documents/{wcId}/original` | Get original document from working copy |

## 5. Entity Schema

```sql
-- Migration 038
ALTER TABLE documents ADD COLUMN working_copy_of UUID;
ALTER TABLE documents ADD COLUMN is_working_copy BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_document_working_copy_of ON documents (working_copy_of);
CREATE INDEX idx_document_is_working_copy ON documents (is_working_copy);
```

## 6. Service API

```java
public class CheckOutCheckInService {
    Document checkout(UUID documentId);
    Document checkout(UUID documentId, UUID destinationFolderId);
    Document checkin(UUID workingCopyId, boolean keepCheckedOut);
    Document cancelCheckout(UUID documentOrWorkingCopyId);
    Optional<Document> getWorkingCopy(UUID originalDocumentId);
    Optional<Document> getOriginal(UUID workingCopyId);
    List<Document> getCheckedOutWorkingCopies(String userId);
}
```

## 7. Working-Copy Lifecycle

```
checkout(docId)
  ├── validate: not checked out, not locked by other, WRITE perm
  ├── create Document row: isWorkingCopy=true, workingCopyOf=docId
  │   └── copy: contentId, hash, size, mime, props, metadata
  ├── mark original: checkoutUser, checkoutDate, baseline
  └── return working copy

checkin(wcId, keepCheckedOut)
  ├── validate: isWorkingCopy=true, owner or admin
  ├── propagate changed content → original
  ├── clear checkout state on original
  ├── soft-delete working copy
  └── if keepCheckedOut: re-checkout → new working copy

cancelCheckout(id)  // accepts original OR wc ID
  ├── resolve original + working copy
  ├── validate: owner or admin
  ├── clear checkout state on original
  └── soft-delete working copy
```

## 8. Backward Compatibility

- Existing `/checkout`, `/checkin`, `/cancel-checkout` endpoints are **unchanged**
- Existing `NodeService.checkoutDocument()` / `checkinDocument()` remain intact
- The new `-wc` endpoints are additive
- `NodeDto.from()` now populates `workingCopyOf` / `isWorkingCopy` for all documents
  (both fields are null/false for non-working-copies)

## 9. Test Coverage

### CheckOutCheckInServiceTest (19 tests)

**Checkout (7)**:
- creates working copy in same folder
- creates working copy in specified destination
- rejects already checked-out document
- rejects checkout of a working copy
- rejects without write permission
- rejects deleted document
- copies properties and metadata

**Checkin (5)**:
- soft-deletes working copy and clears state
- rejects non-working-copy
- rejects non-owner non-admin
- admin can checkin foreign working copy
- keepCheckedOut rejects admin takeover

**Cancel (3)**:
- cancel via working copy ID
- cancel via original document ID
- rejects non-owner non-admin

**Queries (4)**:
- getWorkingCopy delegates to repository
- getOriginal returns original
- getOriginal returns empty for non-wc
- getCheckedOutWorkingCopies delegates

### DocumentControllerWorkingCopyTest (8 tests)

- POST checkout-wc returns working copy metadata
- POST checkout-wc with destination passes folder ID
- POST checkin-wc returns original with cleared checkout
- POST checkin-wc rejects non-working-copy
- POST cancel-checkout-wc clears state
- GET working-copy returns wc when exists
- GET working-copy returns 404 when none
- GET original returns original / 404

## 10. Future Work (out of scope)

- Search index integration (adding `workingCopyOf` / `isWorkingCopy` to `NodeDocument`)
- Frontend UI for working-copy workflow
- Version auto-creation on checkin (currently delegated to caller via `file` param)
- Working-copy content divergence tracking
