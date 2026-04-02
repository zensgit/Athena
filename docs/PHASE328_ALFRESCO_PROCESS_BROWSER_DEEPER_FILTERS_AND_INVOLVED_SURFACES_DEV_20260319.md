# Phase 328 - Alfresco Process Browser Deeper Filters And Involved Surfaces

## Goal

Deepen Athena's process browser parity with Alfresco by adding richer discovery filters and bringing involved-people visibility directly into both workflow browsing surfaces.

## Scope

- extend process browser API filters with:
  - `definitionKey`
  - `query` / legacy `q`
- surface the new filters in:
  - embedded process browser in `TasksPage`
  - standalone `WorkflowProcessesPage`
- keep involved-people visibility visible while browsing process details

## Backend Design

`WorkflowService.listProcesses(...)` now accepts richer filters and applies:

- Flowable-native filtering first for `status`, `businessKey`, `startedBy`
- Athena-side filtering for:
  - `definitionKey`
  - fuzzy `query` over definition key/name, business key, starter, decision, status
- in-memory paging after filter application so `paging.totalItems` remains correct

The controller keeps `q` as a legacy alias for `query`.

## Frontend Design

Workflow pages now expose:

- general search
- `Started by` filter
- `Definition key` filter
- involved-people sections alongside process detail

## Files

- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- `ecm-frontend/src/services/workflowService.ts`
- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena now goes beyond the earlier process-browser slice by combining richer filtering with direct actor visibility in both embedded and dedicated workflow browsing surfaces.
