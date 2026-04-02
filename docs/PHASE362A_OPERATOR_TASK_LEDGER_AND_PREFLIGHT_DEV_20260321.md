# Phase 362A: Operator Task Ledger And Preflight

## Date
- 2026-03-21

## Goal
- Land the first operator-detail slice from the Phase 362A backlog.
- Improve batch download start semantics with structured preflight instead of only post-queue task visibility.
- Surface those preflight warnings in the user workflow before work is queued.

## Scope In This Slice
- This slice implements the `preflight` half of Phase 362A.
- Persistent task ledger and acknowledge/dismiss semantics are still deferred.

## Why
- Alfresco is stricter before download tasks are queued, especially around invalid nodes, access checks, and preflight failures.
- Paperless feels sharper in day-to-day operations because it gives operators clearer task semantics and pre-run visibility.
- Athena already had rich post-queue task telemetry, but it still silently skipped missing or unreadable nodes when batch downloads were started.

## Implementation

### 1. Structured batch download preflight service
- Updated [BatchDownloadService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadService.java) with `inspectNodesPreflight`.
- The new preflight summary now tracks:
  - requested vs distinct node count
  - duplicate references
  - included nodes / files / bytes
  - missing nodes
  - deleted nodes
  - forbidden nodes
  - empty readable folders
  - executable vs non-executable result
  - structured warnings and per-node outcomes

### 2. Batch download preflight API
- Added `POST /api/v1/nodes/download/batch-async/preflight` in [BatchDownloadController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java).
- The existing async start endpoint now also runs preflight internally.
- If preflight finds no readable files, queue creation now fails fast with a clear error instead of creating an empty or misleading task.

### 3. FileBrowser consumption
- Updated [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts) with the new preflight types and request method.
- Updated [FileBrowser.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/FileBrowser.tsx) so selection downloads now:
  - call preflight before queueing
  - warn when duplicates or skipped nodes are detected
  - block queueing when no readable files are available
  - fall back to direct ZIP download only with the effective included node set

## Result
- Athena now gives users structured visibility before a batch download task is queued.
- This closes one of the clearest operator-detail gaps versus Alfresco.
- It also creates a clean path for a future persistent operator task ledger, because task creation semantics are no longer purely best-effort.

## Files
- [BatchDownloadController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java)
- [BatchDownloadService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadService.java)
- [BatchDownloadControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java)
- [BatchDownloadServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/BatchDownloadServiceTest.java)
- [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts)
- [FileBrowser.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/FileBrowser.tsx)

## Next
- The remaining Phase 362A work is the durable operator task ledger:
  - persisted task history
  - acknowledge / dismiss semantics
  - cleanup of in-memory-only task state for governance-critical flows
