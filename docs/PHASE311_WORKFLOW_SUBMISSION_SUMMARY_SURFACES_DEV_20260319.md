# Phase 311 - Workflow Submission Summary Surfaces Dev

Date: 2026-03-19

## Goal

Deepen workflow parity by exposing approval submission summaries as first-class workflow resources instead of forcing the UI to parse raw engine variables.

## Backend Submission Summary Model

Updated [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java):

- added `WorkflowSubmissionSummary`
- added `buildSubmissionSummary(...)`
- `getProcessDetail(...)` now returns a normalized submission summary
- `getTaskDetail(...)` now returns the same summary shape for the selected task context

The summary captures the approval-centric signals Athena already persists:

- `approvers`
- `startComment`
- `startFormSubmittedBy`
- `startFormSubmittedAt`
- `decision`
- `decisionLabel`
- `reviewedBy`
- `reviewedAt`
- `comment`

This keeps the transport contract stable while Athena continues to use Flowable internally.

## Workflow Controller Contract

Updated [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java):

- `TaskDetailResponse` now includes `submissionSummary`
- `ProcessDetailResponse` now includes `submissionSummary`
- added `WorkflowSubmissionSummaryResponse`

The REST layer now publishes a structured workflow summary instead of leaving presentation logic to infer meaning from generic variable maps.

## Frontend Task Workspace

Updated [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts):

- added `WorkflowSubmissionSummary`
- extended `WorkflowProcessDetail`
- extended `WorkflowTaskDetail`

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- added `renderSubmissionSummary(...)`
- process summary panel now shows normalized workflow submission details
- task detail panel now shows the same structured summary
- history remains available, but active-task inspection no longer depends on raw variable chips for approval-specific meaning

## Tests

Updated [WorkflowControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java):

- verifies `submissionSummary` on process detail
- verifies `submissionSummary` on task detail
- keeps existing document history assertions intact

## Design Notes

- The summary is intentionally approval-focused because Athena's dominant workflow today is document approval.
- Returning the same summary shape from both process and task detail keeps future workflow side panels and dashboards simpler.
- Raw variables are still available, but the UI now reads business-level fields from the normalized summary first.
