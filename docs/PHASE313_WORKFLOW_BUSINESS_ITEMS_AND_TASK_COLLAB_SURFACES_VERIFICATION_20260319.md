# Phase 313 - Workflow Business Items and Task Collaboration Surfaces Verification

Date: 2026-03-19

## Verified Scope

- workflow process business items rendered in the task workspace
- workflow task business items rendered in the task workspace
- business item preview/discuss deeplinks
- folder-to-browse routing for non-document business items

## Commands

### Frontend

```bash
cd ecm-frontend
npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/services/nodeService.ts
npm run -s build
```

Result:

- Passed

## Manual Verification Expectations

- selecting a task shows both process-level and task-level business items when present
- document business items can open preview directly
- document business items can jump into discussion directly
- folder business items navigate to the browse page instead of opening document preview

## Files Verified

- [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx)
- [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts)
- [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java)
- [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java)
