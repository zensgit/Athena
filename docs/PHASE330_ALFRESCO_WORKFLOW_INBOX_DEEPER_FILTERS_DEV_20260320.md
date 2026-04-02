# Phase 330 - Alfresco Workflow Inbox Deeper Filters

## Goal

Close another workflow inbox parity gap against Alfresco by adding deeper task discovery filters for pooled/shared work while preserving Athena's existing scope-oriented task workbench.

## Scope

- extend workflow task inbox API with:
  - `processId`
  - `owner`
  - `candidateUser`
  - `candidateGroup`
- keep legacy `q` alias support
- expose the most useful new filters in Athena task workbench:
  - `Process ID`
  - `Candidate group`

## Backend Design

`GET /api/v1/workflows/tasks/inbox` now forwards the richer filter set into `WorkflowService.listTasks(...)`.

Task filtering behavior:

- `processId` narrows both active and completed task summaries to one process instance
- `owner` narrows task visibility to tasks owned by a specific actor
- `candidateUser` and `candidateGroup` operate on identity links
- `candidateGroup` is constrained to active pooled/shared scopes, matching the practical Alfresco usage model

The service keeps existing scope semantics intact:

- `ASSIGNED`
- `CLAIMABLE`
- `SHARED`
- `INVOLVED`
- `COMPLETED`
- `ALL_ACTIVE`
- `ALL`

## Frontend Design

`TasksPage` now adds two compact inbox filters above the task list:

- `Process ID`
- `Candidate group`

The candidate-group filter is only enabled for active pooled/shared-capable scopes:

- `Claimable`
- `Unassigned`
- `All available`

This keeps the workflow workbench simple while still surfacing the highest-value pooled-task filter Athena was missing.

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- `ecm-frontend/src/services/workflowService.ts`
- `ecm-frontend/src/pages/TasksPage.tsx`

## Result

Athena now moves closer to Alfresco's richer workflow inbox search model by supporting process-scoped and candidate-scoped task discovery, while keeping the current workbench interaction model intact.
