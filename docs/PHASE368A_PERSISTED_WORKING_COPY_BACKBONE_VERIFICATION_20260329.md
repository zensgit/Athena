# Phase 368A — Persisted Working-Copy Backbone — Verification

> **Date**: 2026-03-29

---

## 1. Verification Matrix

| # | Claim | Verify | Status |
|---|-------|--------|--------|
| 1 | `Document.java` has `workingCopyOf` UUID field | `grep "workingCopyOf" entity/Document.java` | PASS |
| 2 | `Document.java` has `isWorkingCopy` boolean field | `grep "isWorkingCopy\|workingCopy" entity/Document.java` | PASS |
| 3 | `checkin()` clears `workingCopyOf` and `workingCopy` | Read lines 155-162 of Document.java | PASS |
| 4 | `NodeDto` record includes `workingCopyOf` and `isWorkingCopy` | `grep "workingCopyOf\|isWorkingCopy" dto/NodeDto.java` | PASS |
| 5 | `NodeDto.from()` populates both fields from Document | Read NodeDto.java `from()` method | PASS |
| 6 | `NodeDto.withPreviewSemantics()` passes through both fields | Read NodeDto.java `withPreviewSemantics()` | PASS |
| 7 | `DocumentRepository` has `findWorkingCopyOf()` | `grep "findWorkingCopyOf" repository/DocumentRepository.java` | PASS |
| 8 | `DocumentRepository` has `findWorkingCopiesByUser()` | `grep "findWorkingCopiesByUser" repository/DocumentRepository.java` | PASS |
| 9 | `CheckOutCheckInService` exists with 7 public methods | Read service/CheckOutCheckInService.java | PASS |
| 10 | `checkout()` creates real Document row with `workingCopy=true` | Read checkout() method + test | PASS |
| 11 | `checkout()` with destination uses specified folder | Read checkout(id, destId) + test | PASS |
| 12 | `checkout()` rejects already checked-out documents | Test: `rejectsAlreadyCheckedOut` | PASS |
| 13 | `checkout()` rejects working copies as input | Test: `rejectsCheckoutOfWorkingCopy` | PASS |
| 14 | `checkout()` copies contentId, hash, size, props, metadata | Test: `copiesPropertiesAndMetadata` | PASS |
| 15 | `checkin()` soft-deletes working copy | Test: `checkinClearsStateAndDeletesWc` | PASS |
| 16 | `checkin()` propagates changed content to original | Test: `checkinClearsStateAndDeletesWc` — asserts contentId | PASS |
| 17 | `checkin()` skips content propagation when unchanged | Test: `skipsContentPropagationWhenUnchanged` | PASS |
| 18 | `checkin()` rejects non-working-copy input | Test: `rejectsNonWorkingCopy` | PASS |
| 19 | `cancelCheckout()` accepts working copy ID | Test: `cancelViaWorkingCopyId` | PASS |
| 20 | `cancelCheckout()` accepts original document ID | Test: `cancelViaOriginalId` | PASS |
| 21 | DB migration 038 adds 2 columns + 2 indexes | Read 038-*.xml | PASS |
| 22 | Migration registered in db.changelog-master.xml | `grep "038" db.changelog-master.xml` | PASS |
| 23 | Controller has `checkout-wc` endpoint | `grep "checkout-wc" DocumentController.java` | PASS |
| 24 | Controller has `checkin-wc` endpoint | `grep "checkin-wc" DocumentController.java` | PASS |
| 25 | Controller has `cancel-checkout-wc` endpoint | `grep "cancel-checkout-wc" DocumentController.java` | PASS |
| 26 | Controller has `working-copy` GET endpoint | `grep "working-copy" DocumentController.java` | PASS |
| 27 | Controller has `original` GET endpoint | `grep "original" DocumentController.java` | PASS |
| 28 | Existing `/checkout` endpoint unchanged | Diff DocumentController.java — no changes to lines 204-210 | PASS |
| 29 | Existing `/checkin` endpoint unchanged | Diff DocumentController.java — no changes to lines 247-265 | PASS |
| 30 | Existing `/cancel-checkout` endpoint unchanged | Diff DocumentController.java — no changes to lines 267-273 | PASS |

## 2. Hot-File Constraint Verification

| File | Package | Modified? | Required |
|------|---------|-----------|----------|
| PreviewService.java | preview | NO | NO |
| PreviewFailureClassifier.java | preview | NO | NO |
| PreviewQueueService.java | preview | NO | NO |
| PreviewResult.java | preview | NO | NO |
| PreviewPreflightResolver.java | preview | NO | NO |
| FullTextSearchService.java | search | NO | NO |
| FacetedSearchService.java | search | NO | NO |
| SearchFilters.java | search | NO | NO |
| NodeDocument.java | search | NO | NO |
| PreviewStatusFilterHelper.java | search | NO | NO |
| RenditionResourceService.java | service | NO | NO |
| RenditionResourceController.java | controller | NO | NO |

**Result**: Zero hot files touched.

## 3. Test Inventory

### CheckOutCheckInServiceTest.java — 19 tests

```
Checkout:
  ✓ creates persisted working copy in same folder
  ✓ creates working copy in specified destination folder
  ✓ rejects checkout of already checked-out document
  ✓ rejects checkout of a working copy
  ✓ rejects checkout without write permission
  ✓ rejects checkout of deleted document
  ✓ rejects checkout when locked by another user
  ✓ copies properties and metadata to working copy

Checkin:
  ✓ soft-deletes working copy and clears original checkout state
  ✓ rejects checkin of non-working-copy
  ✓ rejects checkin by non-owner non-admin
  ✓ admin can check in another user's working copy
  ✓ keepCheckedOut rejects admin taking over ownership
  ✓ does not propagate content when unchanged

CancelCheckout:
  ✓ accepts working copy ID and deletes it
  ✓ accepts original document ID and finds working copy to delete
  ✓ rejects cancel by non-owner non-admin

Queries:
  ✓ getWorkingCopy delegates to repository
  ✓ getOriginal returns original document for a working copy
  ✓ getOriginal returns empty for non-working-copy
  ✓ getCheckedOutWorkingCopies delegates to repository
```

### DocumentControllerWorkingCopyTest.java — 8 tests

```
  ✓ POST checkout-wc returns working copy with isWorkingCopy=true
  ✓ POST checkout-wc with destination passes folder ID
  ✓ POST checkin-wc returns original document with cleared checkout
  ✓ POST checkin-wc rejects non-working-copy
  ✓ POST cancel-checkout-wc returns original with cleared state
  ✓ GET working-copy returns working copy when exists
  ✓ GET working-copy returns 404 when none exists
  ✓ GET original returns original document from working copy ID
```

## 4. Compilation Verification

```bash
# Verify new files compile (no preview/search/rendition imports)
grep -c "import.*preview\|import.*search\|import.*Rendition" \
  ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java
# Expected: 0
```

## 5. Schema Verification

```sql
-- After migration 038 runs:
\d documents
-- Should show:
--   working_copy_of   | uuid    | nullable
--   is_working_copy   | boolean | NOT NULL, default false

-- Indexes:
--   idx_document_working_copy_of  ON documents (working_copy_of)
--   idx_document_is_working_copy  ON documents (is_working_copy)
```

## 6. Endpoint Smoke-Test Commands

```bash
# Checkout with working copy
curl -X POST http://localhost:8080/api/documents/{docId}/checkout-wc

# Checkout to different folder
curl -X POST "http://localhost:8080/api/documents/{docId}/checkout-wc?destination={folderId}"

# Get working copy
curl http://localhost:8080/api/documents/{docId}/working-copy

# Get original from working copy
curl http://localhost:8080/api/documents/{wcId}/original

# Checkin working copy
curl -X POST http://localhost:8080/api/documents/{wcId}/checkin-wc

# Cancel checkout
curl -X POST http://localhost:8080/api/documents/{docId}/cancel-checkout-wc
```

## 7. Regression Checks

| Existing Feature | Impact | Check |
|-----------------|--------|-------|
| Existing checkout/checkin endpoints | None — unchanged | Run `DocumentControllerCheckoutTest` |
| NodeDto serialization | +2 fields added to JSON output | `NodeDtoTest` if exists |
| Checkout graph endpoint | Unchanged — still virtual | Run `NodeServiceCheckoutTest` |
| Document entity save/load | +2 nullable columns | Existing `NodeServiceCheckoutTest` |
| Search index projection | **Not updated** — `NodeDocument` untouched | Expected: search won't filter by `isWorkingCopy` yet |

## 8. Summary

- **27 focused tests** (19 service + 8 controller)
- **0 hot files modified**
- **5 new endpoints** added with `-wc` suffix
- **Full backward compatibility** with existing checkout flow
- **1 DB migration** (038) — additive, nullable columns + indexes
