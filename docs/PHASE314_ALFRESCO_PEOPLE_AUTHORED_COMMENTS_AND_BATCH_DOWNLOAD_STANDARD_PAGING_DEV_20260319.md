# Phase 314 - Alfresco People Authored Comments and Batch Download Standard Paging Dev

Date: 2026-03-19

## Goal

Extend Athena's collaboration and operations parity by exposing authored comments in People Directory and standardizing admin batch download task-center paging around `maxItems/skipCount/paging`.

## People Directory Authored Comments

Updated [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx):

- added `selectedAuthoredComments`
- profile loading now fetches `commentService.getUserComments(selectedUsername, 0, 6)`
- added `Authored Comments` panel beside existing mention-driven collaboration surfaces
- added `Preview` and `Discuss` actions for authored comments

Updated [commentService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/commentService.ts):

- reused user-scoped comments transport for profile-side collaboration entry points

This complements `mentioned-comments` by showing what the person authored, not only where they were mentioned.

## Batch Download Standard Paging Contract

Updated [BatchDownloadAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java):

- `snapshot(...)` now accepts `limit`, `skipCount`, and `statusFilter`
- snapshot metadata now returns `filteredCount`, `skipCount`, and `maxItems`

Updated [BatchDownloadController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java):

- `GET /api/v1/nodes/download/batch-async` now accepts `maxItems`
- `GET /api/v1/nodes/download/batch-async` now accepts `skipCount`
- legacy `limit` remains accepted for compatibility
- response now includes `paging`

Updated [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts):

- `listBatchDownloadAsyncTasks(...)` now sends `maxItems` and `skipCount`
- client response typing now understands `paging`

## Batch Download Admin Task Center

Updated [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx):

- added batch download page state
- added rows-per-page state
- added paging state
- added page underflow recovery when cleanup/filtering empties the current page
- status filter resets back to page `0`
- refresh and auto-refresh now preserve the current page
- task center now shows `Rows / page`
- task center now shows `Listed x-y of total`
- task center now shows `Prev` and `Next`

## Tests

Updated backend tests:

- [BatchDownloadControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java)
- [BatchDownloadAsyncTaskRegistryTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistryTest.java)

New assertions cover:

- `maxItems/skipCount` paging
- filtered totals
- `hasMoreItems`
- paging metadata defaults on the admin list endpoint

## Design Notes

- The paging shape now matches the standard async task-center contract already used elsewhere in Athena.
- People Directory keeps collaboration entry points symmetrical: favorites, mentions, and authored comments all route into the same preview/discussion surface.
- Admin operators can now work through larger download queues without loading an unbounded task table.
