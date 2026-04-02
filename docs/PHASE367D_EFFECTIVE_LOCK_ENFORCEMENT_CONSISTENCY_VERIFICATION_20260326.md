# Phase 367D: Effective Lock Enforcement Consistency Verification

## Backend

- `cd ecm-core && mvn -q -Dtest=FolderServiceLockSemanticsTest,VersionServiceLockSemanticsTest,NodeDocumentLockProjectionTest,NodeServiceLockTest test`

## Frontend

- No frontend source changes in this slice.

## Diff hygiene

- `git diff --check -- ecm-core/src/main/java/com/ecm/core/entity/Node.java ecm-core/src/main/java/com/ecm/core/service/NodeService.java ecm-core/src/main/java/com/ecm/core/service/FolderService.java ecm-core/src/main/java/com/ecm/core/service/VersionService.java ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java ecm-core/src/test/java/com/ecm/core/service/FolderServiceLockSemanticsTest.java ecm-core/src/test/java/com/ecm/core/service/VersionServiceLockSemanticsTest.java ecm-core/src/test/java/com/ecm/core/search/NodeDocumentLockProjectionTest.java docs/PHASE367D_EFFECTIVE_LOCK_ENFORCEMENT_CONSISTENCY_DEV_20260326.md docs/PHASE367D_EFFECTIVE_LOCK_ENFORCEMENT_CONSISTENCY_VERIFICATION_20260326.md`

## Scope verified

- Folder update clears expired locks before write enforcement.
- Version creation clears expired locks and rejects only effective foreign locks.
- Lock conflict messages include active lifetime details.
- Search projection no longer reports expired locks as active.

## Notes

- This phase does not yet add read-only lock type behavior, bulk lock orchestration, or working-copy relationships.
- The broader “surpass benchmark in all functional, operational, and detail surfaces” goal remains in progress.
