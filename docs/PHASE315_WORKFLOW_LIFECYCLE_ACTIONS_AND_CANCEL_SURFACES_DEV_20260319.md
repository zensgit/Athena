# Phase 315 - Workflow Lifecycle Actions and Cancel Surfaces Dev

Date: 2026-03-19

## Goal

Move Athena workflow parity from read-only lifecycle inspection into actionable task/process operations by exposing claim, release, and business-facing process cancel flows.

## Backend Workflow Lifecycle Actions

Updated [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java):

- added `claimTask(...)`
- added `unclaimTask(...)`
- added `cancelProcess(...)`

Behavior:

- `claimTask(...)` claims an unassigned task for the current user
- `unclaimTask(...)` releases a claimed task when the current user is the assignee or admin
- `cancelProcess(...)` wraps runtime deletion in business-facing cancel semantics and accepts an optional reason

Updated [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java):

- added `POST /api/v1/workflows/tasks/{taskId}/claim`
- added `POST /api/v1/workflows/tasks/{taskId}/unclaim`
- added `POST /api/v1/workflows/processes/{processId}/cancel`

This keeps the older delete endpoint for compatibility while giving the UI a clearer process-cancel contract.

## Frontend Workflow Workspace

Updated [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts):

- added `claimTask(...)`
- added `unclaimTask(...)`
- added `cancelProcess(...)`

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- added release action for claimed tasks in the left task list
- added claim action for unassigned items in `Current Process Tasks`
- added `Cancel workflow` action in `Process Summary`
- task/process lifecycle actions now refresh task state after completion

## Design Notes

- Claim is surfaced where it is operationally useful: on process-task rows that are currently unassigned.
- Release stays on the assignee's own task list because that is the common “give it back to the pool” workflow.
- Process cancel is expressed as cancel, not delete, to better match business/operator semantics.

## Tests

Updated [WorkflowControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java):

- verifies task claim delegates to service
- verifies task unclaim delegates to service
- verifies process cancel delegates to service
