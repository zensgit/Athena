# Phase 307 - Alfresco Workflow Form Submit and Batch Download Governance Dev

Date: 2026-03-19

## Goal

Close the next operational parity gap by adding:

- workflow start-form submit endpoint
- workflow task-form submit endpoint
- normalized workflow variable persistence for approval flows
- admin cleanup and bulk active-cancel APIs for async batch downloads
- frontend consumption for the new workflow submit and download governance contracts

## Workflow Submit Contracts

Updated [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java):

- `POST /api/v1/workflows/document/{documentId}/approval/form-submit`
- `POST /api/v1/workflows/tasks/{taskId}/task-form-submit`

These endpoints accept a stable `values` payload so Athena can evolve toward Alfresco-style form submit semantics without binding the frontend to raw Flowable variable shapes.

## Workflow Variable Normalization

Updated [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java):

- added `submitStartForm`
- added `submitTaskForm`
- normalized start payload into:
  - `approvers`
  - `startComment`
  - `startFormSubmittedBy`
  - `startFormSubmittedAt`
- normalized task payload into:
  - `approved`
  - `decision`
  - `decisionLabel`
  - `comment`
  - `reviewedBy`
  - `reviewedAt`

This keeps the published form model and the persisted runtime variables aligned.

## Batch Download Governance APIs

Updated [BatchDownloadController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java) and [BatchDownloadAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java):

- `GET /api/v1/nodes/download/batch-async/summary`
- `POST /api/v1/nodes/download/batch-async/cleanup`
- `POST /api/v1/nodes/download/batch-async/cancel-active`

The registry now exposes lifecycle summary counts plus terminal cleanup and active-task cancellation helpers. This moves admin governance beyond per-task actions and toward an ops-friendly control surface.

## Frontend Integration

Updated [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts):

- added `submitStartForm`
- added `submitTaskForm`

Updated [StartWorkflowDialog.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/dialogs/StartWorkflowDialog.tsx):

- submit now goes through `approval/form-submit`
- UI still renders from the published start form model

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- task decisions now go through `task-form-submit`
- detail dialog continues to bind to task form metadata

Updated [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts) and [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx):

- added download task summary contract
- added cleanup action
- added cancel-active action
- made the admin task center load both task list and lifecycle summary

## Design Notes

- Workflow submit normalization is currently optimized for the `documentApproval` contract, but the transport shape is generic enough for future workflow definitions.
- Batch download cleanup intentionally only allows terminal states; active-task bulk controls are isolated behind `cancel-active` to keep lifecycle transitions explicit.
