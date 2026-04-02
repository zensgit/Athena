# Phase 322 - Alfresco Workflow Workbench Scope and Search Surfaces Verification

Date: 2026-03-19

## Commands

- `cd ecm-frontend && npx eslint --max-warnings=0 src/services/workflowService.ts src/pages/TasksPage.tsx`
- `cd ecm-frontend && npm run -s build`

## Verification Notes

- `TasksPage` compiles with inbox scope filtering, debounced search, richer task card metadata, and completed-task read-only handling.
- shared queue items now surface `Claim` directly from the inbox list.
- `workflowService` consumes the Alfresco-style task inbox alias route and exposes a process browser list contract.
