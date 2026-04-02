# Phase 321 - Alfresco Workflow Inbox Scope and Search API Dev

Date: 2026-03-19

## Goal

Close one of the biggest remaining workflow gaps against the Alfresco reference:

- list workflow tasks by inbox scope instead of only `my assigned`
- search task inbox entries by workflow/business metadata
- expose a stable process browser API for active/completed workflow instances

## Backend Delivery

Updated [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java):

- added task inbox listing with scope normalization for `ASSIGNED`, `SHARED`, `CLAIMABLE`, `COMPLETED`, `ALL_ACTIVE`, and `ALL`
- added in-memory search across task name, description, assignee, workflow definition, business key, and starter
- added optional assignee override for admin-style inbox browsing and stable `status` / `completedAt` output for completed items
- added process browser listing over historic process instances with `status`, `businessKey`, `startedBy`, and paging
- introduced stable service records for task summaries and process browser pages

Updated [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java):

- added `GET /api/v1/workflows/tasks` and Alfresco-style alias `GET /api/v1/workflows/tasks/inbox`
- added `GET /api/v1/workflows/processes` and Alfresco-style alias `GET /api/v1/workflows/processes/browser`
- accepts `query` with `q` fallback plus optional `assignee`
- kept existing detail endpoints unchanged for compatibility

Updated [WorkflowControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java):

- added task inbox route coverage
- added process browser route coverage

## Outcome

Athena now has a first-class workflow inbox and process-browser API surface instead of a single “my tasks” endpoint. This moves the backend much closer to Alfresco’s task/process browsing model and creates a stable base for richer workbench UI.
