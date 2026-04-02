# Phase 322 - Alfresco Workflow Workbench Scope and Search Surfaces Dev

Date: 2026-03-19

## Goal

Turn Athena’s workflow page into a real workbench instead of a fixed “my tasks” list:

- switch between personal inbox, shared queue, and broader active inbox
- search tasks by workflow metadata and business key
- show richer task cards with process and business-key context
- keep shared-queue claim behavior explicit in the main list

## Frontend Delivery

Updated [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts):

- task inbox calls now use the Alfresco-style `/workflows/tasks/inbox` alias route
- added scope mapping from UI-friendly values to API values
- added completed inbox scope, `status` / `completedAt` task metadata, and process browser service contract for follow-up UI work

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- added inbox scope selector including completed history and a debounced search box
- upgraded inbox item cards with process definition and business key context
- removed the old frontend-only fallback notice
- shared queue tasks now show an explicit `Claim` action in the inbox list before approval actions
- completed inbox items now downgrade into read-only detail mode while still surfacing process summary and workflow history

## Outcome

Athena’s workflow workbench now behaves more like a real inbox browser and less like a narrow task detail launcher. This is a direct parity improvement against the reference workflow UI and a stronger base for pooled/shared workflow operations.
