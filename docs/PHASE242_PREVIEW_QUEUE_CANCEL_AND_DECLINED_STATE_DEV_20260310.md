# Phase 242 - Preview Queue Cancel Protocol + Declined State Semantics (Dev)

Date: 2026-03-10  
Scope: `ecm-core` + `ecm-frontend`

## 1. Goals

1. Add explicit preview queue cancel protocol for queued/running tasks.
2. Align batch queue API semantics with clear per-item queue state (`QUEUED` / `DECLINED` / `FAILED`).
3. Expose cancel operation in document preview API and frontend service contract.

## 2. Backend Implementation

## 2.1 Preview queue cancel protocol

File: `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

- Added cancellation status contract:
  - `PreviewQueueCancellationStatus`
  - states: `CANCELLED`, `CANCEL_REQUESTED`, `IDLE`
- Added public cancel entrypoint:
  - `cancel(UUID documentId)`
- In-memory queue cancel behavior:
  - queued job: remove from active queue and return `CANCELLED`
  - running job: record cancel request and return `CANCEL_REQUESTED`
  - no active job: return `IDLE`
- Redis backend cancel behavior:
  - running lock present: write cancel request marker and return `CANCEL_REQUESTED`
  - scheduled entry present: complete task and return `CANCELLED`
  - no active job: return `IDLE`
- Fixed cancel precedence:
  - running state is now checked before queued state to avoid misclassifying running tasks as `CANCELLED`.

## 2.2 Document API for preview queue cancel

File: `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`

- Added endpoint:
  - `POST /api/v1/documents/{documentId}/preview/queue/cancel`
- Added response DTO:
  - `PreviewQueueCancelResponse`
  - fields: `documentId`, `queueState`, `cancelled`, `hadActiveTask`, `running`, `message`

## 2.3 Search batch queue semantics

File: `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`

- Extended batch item DTO to expose `queueState`.
- Queue execution now returns explicit state:
  - queued -> `queueState=QUEUED`
  - skipped/declined -> `queueState=DECLINED`
  - execution error -> `queueState=FAILED`

## 3. Frontend Contract Update

Files:
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

- Added cancel API client:
  - `cancelQueuedPreview(nodeId: string)`
- Added/extended response models:
  - `PreviewQueueCancelStatus`
  - `PreviewQueueSearchBatchItem.queueState`
- Advanced Search queue tooltip now surfaces:
  - `queueState`
  - queue message
  - retry attempts and next retry time

## 4. Tests Updated

- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
  - covered queued cancel + running cancel request semantics.
- `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java`
  - added cancel endpoint response coverage.
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
  - asserts `queueState=QUEUED` for enqueue success.
  - asserts `queueState=DECLINED` for skipped/declined enqueue result.
