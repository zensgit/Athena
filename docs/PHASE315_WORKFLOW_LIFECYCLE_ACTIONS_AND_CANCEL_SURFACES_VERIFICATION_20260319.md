# Phase 315 - Workflow Lifecycle Actions and Cancel Surfaces Verification

Date: 2026-03-19

## Verified Scope

- workflow task claim endpoint
- workflow task unclaim endpoint
- workflow process cancel endpoint
- task workspace claim/release/cancel surfaces

## Commands

### Backend

```bash
cd ecm-core
mvn -q -Dtest=WorkflowControllerTest,BatchDownloadControllerTest,BatchDownloadAsyncTaskRegistryTest test
mvn -q -DskipTests compile
```

Result:

- Passed

### Frontend

```bash
cd ecm-frontend
npx eslint --max-warnings=0 src/services/workflowService.ts src/pages/TasksPage.tsx
npm run -s build
```

Result:

- Passed

## Manual Verification Expectations

- current-user tasks can be released back to the shared queue
- unassigned tasks listed under a running process can be claimed into `My Tasks`
- running workflow processes can be cancelled from the task workspace

## Files Verified

- [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java)
- [WorkflowController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java)
- [WorkflowControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java)
- [workflowService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/workflowService.ts)
- [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx)
