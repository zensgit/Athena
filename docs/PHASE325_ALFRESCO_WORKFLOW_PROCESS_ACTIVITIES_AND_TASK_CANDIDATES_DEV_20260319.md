# Phase 325 - Alfresco Workflow Process Activities and Task Candidates Dev

Date: 2026-03-19

## Goal

Close the next workflow lifecycle gap against the Alfresco reference:

- expose process activity timeline resources instead of only process/task summaries
- expose task candidate users/groups for shared and claimable work
- surface the new lifecycle resources in both the workflow workbench and the dedicated process browser page

## Backend Delivery

Updated [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java):

- added `getProcessActivities(processId)` backed by Flowable historic activity instances
- added `getTaskCandidates(taskId)` backed by Flowable task identity links
- introduced stable service records for process activities and task candidates

Updated [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java):

- added `GET /api/v1/workflows/processes/{processId}/activities`
- added `GET /api/v1/workflows/tasks/{taskId}/candidates`
- mapped the new lifecycle resources into stable response DTOs

Updated [WorkflowControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java):

- added coverage for the process activity timeline resource
- added coverage for task candidate user/group resources

## Frontend Delivery

Updated [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts):

- added `WorkflowProcessActivity`
- added `WorkflowTaskCandidate`
- added `getProcessActivities(...)`
- added `getTaskCandidates(...)`

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- task detail now renders a `Task Candidates` panel
- workflow workbench now renders a `Process Activity Timeline` panel
- both surfaces are loaded alongside existing process/task metadata

Updated [WorkflowProcessesPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/WorkflowProcessesPage.tsx):

- dedicated process browser now shows process activity timeline in addition to summary/tasks/variables/business items/history

## Outcome

Athena now exposes deeper workflow lifecycle traceability than before: users can inspect how a process advanced over time and who can claim or receive a task, instead of relying only on current assignee and terminal history summaries.
