# Phase 323 - Alfresco Process Browser Workspace Page Dev

Date: 2026-03-19

## Goal

Close the next obvious workflow surface gap against the Alfresco reference:

- expose a dedicated workflow process browser page instead of only embedding process browsing inside the task workbench
- make process browsing reachable from the main account navigation
- keep direct document collaboration entry points available from process business items

## Frontend Delivery

Added [WorkflowProcessesPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/WorkflowProcessesPage.tsx):

- introduced a dedicated `/workflow-processes` workspace page
- reused the stable workflow process-browser API from phase 321
- added active/completed/all status filtering, business-key search, and paged browsing
- loads process detail, process tasks, process variables, business items, workflow history, BPMN XML, and diagram preview
- supports direct `Preview` / `Discuss` actions for workflow business items through the existing document preview surface

Updated [App.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/App.tsx):

- registered the new private route `/workflow-processes`

Updated [MainLayout.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/layout/MainLayout.tsx):

- added `Workflow Processes` to the account menu so the browser is reachable without entering the task workbench first

Updated [MainLayout.menu.test.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/layout/MainLayout.menu.test.tsx):

- asserted the new workflow-process browser entry for both admin and viewer-style users

## Outcome

Athena now has a first-class process browser workspace instead of treating process browsing as a side panel. This moves the product closer to Alfresco's workflow/process browsing model while keeping Athena's stronger preview/discussion loop directly inside process inspection.
