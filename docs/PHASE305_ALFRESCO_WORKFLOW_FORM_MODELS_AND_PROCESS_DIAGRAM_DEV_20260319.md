# Phase 305 - Alfresco Workflow Form Models and Process Diagram Dev

Date: 2026-03-19

## Goal

Close the next Alfresco workflow parity gap by adding:

- workflow definition `start-form-model`
- workflow task `task-form-model`
- workflow process diagram binary endpoint
- frontend consumption in workflow start and task detail surfaces

## Backend

### New workflow relation resources

Added to [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java):

- `GET /api/v1/workflows/definitions/{definitionId}/start-form-model`
- `GET /api/v1/workflows/tasks/{taskId}/task-form-model`
- `GET /api/v1/workflows/processes/{processId}/diagram`

### Workflow service form model derivation

Added to [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java):

- `getStartFormModel`
- `getTaskFormModel`
- `getProcessDiagram`
- `WorkflowFormModelElement`
- `WorkflowFormOption`

Current BPMN does not declare explicit Flowable `formKey`, so Athena derives a stable form model from the active `documentApproval` process contract:

- start form:
  - `approvers`
  - `comment`
- task form:
  - `approved`
  - `comment`

This preserves REST compatibility without forcing a BPMN redefinition first.

## Frontend

### Workflow service contract

Extended [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts) with:

- `WorkflowFormModelElement`
- `WorkflowFormOption`
- `getStartFormModel`
- `getTaskFormModel`
- `getProcessDiagram`

### Start workflow dialog

Updated [StartWorkflowDialog.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/dialogs/StartWorkflowDialog.tsx) to:

- resolve the `documentApproval` definition
- fetch `start-form-model`
- surface published form fields in the dialog
- bind form labels/placeholders from the returned model

### Task detail page

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx) to:

- fetch `task-form-model`
- display task form field metadata
- prefer process diagram endpoint, fallback to definition diagram
- carry workflow form metadata into the approval/reject dialog

## Notes

- Process diagram currently reuses the deployed workflow diagram resource for the specific process instance. This closes the binary relation gap first; active-node highlighting can be added later if Flowable diagram generation is introduced.
- Form-model derivation is intentionally scoped to the current `documentApproval` workflow. Additional workflows can register their own field sets without breaking the API shape.
