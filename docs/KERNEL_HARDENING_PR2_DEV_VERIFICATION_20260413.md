# Kernel Hardening PR-2: Binary Delete Semantics Refactor — Development & Verification

## Date
2026-04-13

## Scope
PR-2 covers **P0A-3**: Stop business services from directly deciding physical binary deletion.

## Problem Statement

`VersionService.deleteVersion()` calls `contentService.deleteContent()` which attempts physical binary deletion. The `isContentReferenced()` check in `ContentService` only queries the `documents` table — it misses references from `versions`, working copies, and renditions. This can cause data loss when a binary is shared across multiple owners.

## Solution

Replace direct physical deletion calls with reference ledger detachment. Physical deletion is now exclusively handled by the orphan cleanup scheduler in `ContentReferenceService`.

## Changes Made

### VersionService.java
| Change | Before | After |
|--------|--------|-------|
| Constructor | 6 dependencies | 7 dependencies (+ContentReferenceService) |
| `createVersion()` | No ledger tracking | Calls `contentReferenceService.attach(contentId, VERSION, versionId)` after save |
| `deleteVersion()` | `contentService.deleteContent(version.getContentId())` | `contentReferenceService.detach(version.getContentId(), VERSION, versionId)` |

### ContentService.java
| Change | Before | After |
|--------|--------|-------|
| Constructor | 2 dependencies | 3 dependencies (+ContentReferenceRepository) |
| `isContentReferenced()` | Only checks `documentRepository` | Checks content reference ledger first, then falls back to `documentRepository` |

### CheckOutCheckInService.java
| Change | Before | After |
|--------|--------|-------|
| Constructor | 5 dependencies | 6 dependencies (+ContentReferenceService) |
| `checkout()` | No ledger tracking | Calls `contentReferenceService.attach(contentId, WORKING_COPY, wcId)` after WC save |
| `checkin()` | No detach | Calls `contentReferenceService.detach(contentId, WORKING_COPY, wcId)` after WC soft-delete |
| `cancelCheckout()` | No detach | Calls `contentReferenceService.detach(contentId, WORKING_COPY, wcId)` after WC soft-delete |

## Key Design Decisions

1. **No direct physical deletion in business services.** `VersionService.deleteVersion()` no longer calls `contentService.deleteContent()`. It only detaches the reference. The orphan cleanup scheduler handles physical deletion.

2. **Fallback in `isContentReferenced()`**. The ledger is checked first, but the old `documentRepository` query remains as a safety net for any content not yet tracked in the ledger. This ensures backward compatibility.

3. **Attach on create, detach on delete/soft-delete.** Every write path that assigns or removes a content binary now goes through the ledger:
   - `createVersion()` → attach VERSION
   - `deleteVersion()` → detach VERSION
   - `checkout()` → attach WORKING_COPY
   - `checkin()` → detach WORKING_COPY
   - `cancelCheckout()` → detach WORKING_COPY

4. **DOCUMENT owner type is managed by backfill (073) and future upload paths.** PR-2 does not yet add attach/detach calls in the initial document upload flow — that is the responsibility of the pipeline processors. The backfill covers existing documents.

## Files Modified

| File | Lines Changed |
|------|--------------|
| `ecm-core/src/main/java/com/ecm/core/service/VersionService.java` | +3 lines (import, field, attach in create, detach in delete) |
| `ecm-core/src/main/java/com/ecm/core/service/ContentService.java` | +3 lines (import, field, ledger-first isContentReferenced) |
| `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java` | +5 lines (import, field, attach in checkout, detach in checkin + cancel) |

## Files Created

| File | Purpose |
|------|---------|
| `docs/KERNEL_HARDENING_PR2_DEV_VERIFICATION_20260413.md` | This report |

## Exit Criteria Verification

| Criterion | Status |
|-----------|--------|
| Deleting one version never deletes a still-referenced binary | Met: `deleteVersion()` now calls `detach()`, not `deleteContent()` |
| Cleanup only removes binaries with zero active references | Met: Physical deletion deferred to `ContentReferenceService.cleanupOrphanedContent()` |
| Docker build compiles successfully | Pending verification |
| Application starts and is healthy | Pending verification |

## Regression Notes

- `VersionService` now requires `ContentReferenceService` as a constructor dependency. Any test that creates `VersionService` via constructor must include this parameter.
- `CheckOutCheckInService` now requires `ContentReferenceService`. Same note for tests.
- `ContentService` now requires `ContentReferenceRepository`. Same note for tests.
- Existing behavior is preserved: `deleteContent()` still checks references before physical deletion. The ledger adds an additional safety layer on top.

## What This PR Does NOT Do

- Does **not** add DOCUMENT attach/detach to the document upload/delete pipeline — those flows should be addressed when the pipeline processors are refactored.
- Does **not** modify `CheckOutCheckInService.checkin()` to create versions — that is PR-3 (P0A-4).
- Does **not** enable orphan cleanup — it remains `false` by default.

## Next Steps

**PR-3 (P0A-4):** Make `CheckOutCheckInService.checkin()` create a new version via `VersionService.createVersion()`, ensuring the check-in operation enters the version chain.
