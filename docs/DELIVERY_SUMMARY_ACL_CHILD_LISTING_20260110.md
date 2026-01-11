# Delivery Summary: ACL Child Listing & E2E Coverage (2026-01-10)

## Scope
- Filter folder/node child listings by READ permissions for non-admins before paging.
- Add backend unit tests for ACL filtering on child listings.
- Add browse ACL E2E coverage and stabilize viewer search ACL E2E check.
- Record backend and E2E verification evidence.

## Key Changes
- Backend ACL filtering: `ecm-core/src/main/java/com/ecm/core/service/FolderService.java`, `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`, `ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java`
- Backend tests: `ecm-core/src/test/java/com/ecm/core/service/FolderServiceContentsAclTest.java`, `ecm-core/src/test/java/com/ecm/core/service/NodeServiceChildrenAclTest.java`
- Frontend E2E: `ecm-frontend/e2e/browse-acl.spec.ts`, `ecm-frontend/e2e/search-view.spec.ts`
- Docs: design + verification entries and dashboard/index updates.

## Verification
- Backend unit tests: `mvn test -Dtest=NodeServiceChildrenAclTest,FolderServiceContentsAclTest`
- Full backend suite: `mvn test` (88 tests)
- E2E browse ACL: `npx playwright test e2e/browse-acl.spec.ts`
- UI smoke + browse ACL: `npx playwright test e2e/ui-smoke.spec.ts e2e/browse-acl.spec.ts`
- Full E2E: `npx playwright test` (19 tests)

## Risks / Notes
- Non-admin child listing now loads all children for ACL filtering before paging; large folders may incur heavier reads.
- Search ACL UI empty-state relies on index freshness; E2E now asserts API payload for stability.

## Rollback
- Revert the following commits if needed:
  - `fix(core): filter child listings by ACL`
  - `fix(frontend): stabilize ACL E2E checks`
