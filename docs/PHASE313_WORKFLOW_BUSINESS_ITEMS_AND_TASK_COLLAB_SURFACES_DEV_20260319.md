# Phase 313 - Workflow Business Items and Task Collaboration Surfaces Dev

Date: 2026-03-19

## Goal

Deepen Alfresco-style workflow parity by turning workflow business items into first-class task workspace surfaces instead of leaving them as backend-only relation resources.

## Workflow Business Item Surfaces

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- consumed existing `workflowService.getProcessItems(...)`
- consumed existing `workflowService.getTaskItems(...)`
- added `renderBusinessItems(...)`
- rendered `Process Business Items`
- rendered `Task Business Items`

Each business item card now exposes:

- item name
- node type
- repository path
- business key
- source relation

## Task Workspace Actions

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- folder business items open directly in browse
- document business items support `Preview`
- document business items support `Discuss`
- document discussion reuses the existing [DocumentPreview.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/preview/DocumentPreview.tsx) flow

This keeps workflow, content, and collaboration in one workspace instead of forcing the operator to manually search for the attached business object.

## Backend Contract Reuse

This phase intentionally reused the already-delivered workflow resource layer:

- [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java)
- [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java)
- [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts)

No controller contract changes were required for this slice because the business-item endpoints were already available and stable.

## Design Notes

- Workflow detail now behaves more like a content workbench than a metadata-only inspector.
- `Preview` and `Discuss` keep parity with the collaboration surfaces already added to favorites and people views.
- The UI stays tolerant of mixed content by routing folders to browse and documents to preview.
