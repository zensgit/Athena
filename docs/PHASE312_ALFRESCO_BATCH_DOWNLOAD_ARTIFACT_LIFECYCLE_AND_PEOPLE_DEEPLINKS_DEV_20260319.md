# Phase 312 - Alfresco Batch Download Artifact Lifecycle and People Deeplinks Dev

Date: 2026-03-19

## Goal

Push the next Alfresco-style parity slice by improving async download artifact governance and turning People Directory into a real collaboration jump surface.

## Batch Download Artifact Lifecycle

Updated [BatchDownloadAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java):

- added `cleanupEligible()`
- added `retentionExpiresAt()`
- added `artifactPresent()`
- added `archiveSizeBytes()`

Updated [BatchDownloadController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java):

- async task status now includes:
  - `cleanupUrl`
  - `archiveSizeBytes`
  - `retentionExpiresAt`
  - `cleanupEligible`
  - `artifactPresent`
- added single-task cleanup endpoint:
  - `POST /api/v1/nodes/download/batch-async/{taskId}/cleanup`

This moves Athena closer to Alfresco's download-resource style lifecycle surface while giving operators enough metadata to govern artifacts directly.

## Admin Dashboard Lifecycle Surface

Updated [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts):

- extended `BatchDownloadAsyncTask` with lifecycle metadata
- added `cleanupBatchDownloadAsyncTask(taskId)`

Updated [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx):

- batch download task rows now show retention expiry
- rows now show archive size when available
- rows now expose per-task cleanup for terminal tasks
- existing bulk cleanup and auto-refresh remain intact

## People Collaboration Deeplinks

Updated [CommentDto.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/dto/CommentDto.java):

- user-scoped comment payloads now include:
  - `nodeId`
  - `nodeName`
  - `nodeType`

Updated [commentService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/commentService.ts):

- comment type now exposes node metadata for deeplink flows

Updated [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx):

- favorites panel now supports direct `Preview` and `Discuss` actions for document favorites
- mentioned comments now show their source document and support direct preview/discussion
- page lazy-loads [DocumentPreview.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/preview/DocumentPreview.tsx) as an inline collaboration handoff

This takes People Directory beyond profile lookup and toward an Alfresco-style cross-surface collaboration launcher.

## Tests

Updated:

- [BatchDownloadControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java)
- [BatchDownloadAsyncTaskRegistryTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistryTest.java)
- [CommentControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/CommentControllerTest.java)

Coverage now includes lifecycle metadata, single-task cleanup, artifact inspection, and comment node metadata mapping.

## Design Notes

- Batch download lifecycle metadata is computed from the registry state so the controller stays thin.
- Per-task cleanup is limited to terminal tasks to keep destructive actions explicit.
- People Directory deeplinks reuse existing preview/comment infrastructure rather than introducing a separate collaboration modal.
