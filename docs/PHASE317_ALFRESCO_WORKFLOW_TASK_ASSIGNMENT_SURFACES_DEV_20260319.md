# Phase 317 - Alfresco Workflow Task Assignment Surfaces Dev

Date: 2026-03-19

## Goal

Move Athena workflow parity one step closer to Alfresco task lifecycle management by exposing business-facing task assignment and reassignment actions in both the API and task workspace.

## Reference Alignment

Relevant Alfresco workflow task lifecycle semantics are centered on task update operations:

- `PUT /tasks/{taskId}` with assignee updates for reassign
- `state=delegated` with assignee for delegate
- `state=claimed` / `state=unclaimed` for claim and release

Athena already had dedicated claim / unclaim actions. This phase adds the missing reassignment surface through an explicit task assignment API and UI control.

## Backend Changes

Updated [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java):

- added `assignTask(...)`
- validates target username exists in `UserRepository`
- allows reassignment when the current user is the task assignee or an admin
- allows assigning an unclaimed task to another user only for admins

Updated [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java):

- added `POST /api/v1/workflows/tasks/{taskId}/assign`
- added `AssignTaskRequest`

Updated [WorkflowControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java):

- added controller contract coverage for the assign endpoint

## Frontend Changes

Updated [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts):

- added `assignTask(...)`

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- added `Assign Task` action in the task detail surface
- added assignment dialog with username input
- refreshes the task list after reassignment so the current user sees the updated queue state immediately

## Outcome

Athena task users can now move a claimed task to another user directly from the workflow workspace instead of relying on backend-only lifecycle controls.
