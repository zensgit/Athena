# Phase 316 - Alfresco Download Admin Query Filter and People Profile Deeplinks Dev

Date: 2026-03-19

## Goal

Improve operator ergonomics and collaboration parity by letting admins locate batch download tasks by query and letting users jump into People Directory from favorites/profile entry points.

## Batch Download Query Filtering

Updated [BatchDownloadAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java):

- extended `snapshot(...)` with `query`
- query filtering matches `taskId`
- query filtering matches `name`
- query filtering matches `filename`
- query filtering matches `status`

Updated [BatchDownloadController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java):

- `GET /api/v1/nodes/download/batch-async` now accepts `q`
- query filtering composes with `status`, `maxItems`, and `skipCount`

Updated [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts):

- `listBatchDownloadAsyncTasks(...)` now accepts `query`

Updated [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx):

- added batch download search input
- added explicit `Search`
- added `Clear`
- query composes with status filter and paging
- auto-refresh preserves active query

## People Directory Deeplinks

Updated [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx):

- supports `?username=...` preselection

Updated [FavoritesPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/FavoritesPage.tsx):

- added creator profile deeplink
- routes favorites author actions into `/people-directory?username=...`

## Tests

Updated backend tests:

- [BatchDownloadControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java)
- [BatchDownloadAsyncTaskRegistryTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistryTest.java)

New coverage includes:

- status + query filtering
- filtered snapshot totals
- paged query results

## Design Notes

- Query search is intentionally explicit instead of keypress-triggered to keep the admin dashboard stable during auto-refresh.
- People Directory deeplinks now work as a shared target for favorites and profile-oriented collaboration flows.
