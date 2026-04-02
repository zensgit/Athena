# Phase 327 - Alfresco Workflow Involved Scope And Actor Surfaces

## Goal

Close the next workflow parity gap against Alfresco by exposing an explicit `INVOLVED` inbox scope and stable actor resources across task/process workflow surfaces.

## Scope

- Extend task inbox API/UI with `INVOLVED` scope
- Add stable involved-actor resources:
  - `GET /api/v1/workflows/processes/{processId}/involved`
  - `GET /api/v1/workflows/tasks/{taskId}/involved`
- Surface involved people/groups in:
  - task workbench detail panel
  - completed task read-only detail
  - workflow process workspace page

## Backend Design

### Involved scope and actors

Athena now aggregates workflow actors into a stable resource:

- user actors:
  - starter
  - submittedBy
  - approver
  - reviewer
  - assignee
  - owner
  - currentAssignee
  - activityAssignee
  - identity-link users
- group actors:
  - identity-link groups

Each response item includes:

- `userId` or `groupId`
- `displayName`
- deduplicated `roles`

Task inbox scope now also supports `INVOLVED`, allowing a user to find tasks they participate in even if they are not the direct assignee.

## Frontend Design

### Tasks page

- added `Involved` inbox scope
- added involved-people section in:
  - active task detail
  - completed task read-only detail
  - process summary

### Workflow Processes page

- added `Involved People` panel backed by the same stable resource

## Files

- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- `ecm-frontend/src/services/workflowService.ts`
- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena now closes the involved-user parity gap with a direct inbox scope plus reusable actor audit surfaces across task and process workbenches.
