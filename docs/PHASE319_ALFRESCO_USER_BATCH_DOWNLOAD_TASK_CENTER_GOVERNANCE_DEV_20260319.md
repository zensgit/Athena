# Phase 319 - Alfresco User Batch Download Task Center Governance Dev

Date: 2026-03-19

## Goal

Push Athena past simple user download parity by turning the file-browser batch ZIP panel into a real user task center:

- search user-owned tasks by filename or task id
- filter by task status
- paginate with standard `Rows / page`, `Prev`, `Next`, and listed range
- auto-refresh active tasks
- let end users clean up completed terminal tasks from their own surface

## Scope

Updated [FileBrowser.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/FileBrowser.tsx):

- added owner-scoped query input and status filter
- added paging state and `paging` consumption from the existing batch-download API
- added active/total summary chips and last-updated metadata
- added auto-refresh toggle for queued/running/cancel-requested tasks
- added per-task `Dismiss` cleanup action for cleanup-eligible terminal tasks

Reused existing richer list contract in [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts), so this phase did not need new backend endpoints.

## Outcome

Athena now gives end users a self-service download operations surface instead of a small recent-task table. That closes a practical gap with Alfresco-style downloads and goes further by exposing richer in-app governance controls directly where users start the download.
