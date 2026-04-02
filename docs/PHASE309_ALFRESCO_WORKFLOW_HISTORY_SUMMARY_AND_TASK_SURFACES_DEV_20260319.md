# Phase 309 - Alfresco Workflow History Summary and Task Surfaces Dev

Date: 2026-03-19

## Goal

Push workflow parity further by adding:

- richer document workflow history payloads
- stable review/start metadata for approval timelines
- improved task-detail rendering in the workflow workspace

## Backend History Summary Contract

Updated [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java) and [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java):

- `GET /api/v1/workflows/document/{documentId}/history` now returns enriched summary items
- each history item now exposes:
  - `id`
  - `businessKey`
  - `processDefinitionKey`
  - `processDefinitionName`
  - `startTime`
  - `endTime`
  - `startedBy`
  - `startComment`
  - `approvers`
  - `decision`
  - `decisionLabel`
  - `reviewedBy`
  - `reviewedAt`
  - `comment`
  - `ended`

The service now maps approval-centric runtime and historic variables into a stable DTO instead of leaking raw Flowable variable structures directly to the UI.

## Test Coverage

Updated [WorkflowControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java):

- added assertions for the enriched history payload
- verifies start/review metadata and decision normalization remain available through the public API

## Frontend Task Surface

Updated [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts):

- expanded `WorkflowHistoryItem` to match the richer backend contract

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- workflow history rows now render:
  - process definition name/key
  - workflow id
  - start/end timestamps
  - started by
  - reviewed by / reviewed at
  - start note
  - review note
  - decision chip
  - running/ended chip
  - approver chips

This makes the task workspace closer to Alfresco's auditability model while remaining compact enough for Athena's current task UX.

## Design Notes

- The history contract is intentionally summary-oriented rather than a raw variable dump so future UI surfaces can reuse it without backend-specific parsing.
- `decisionLabel` is returned alongside `decision` to keep presentation logic stable if future workflows use non-boolean outcome values.
- Task history now prefers business workflow signals over engine internals, which improves parity and usability at the same time.
