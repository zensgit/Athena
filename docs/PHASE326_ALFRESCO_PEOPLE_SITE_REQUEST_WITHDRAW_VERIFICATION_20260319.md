# Phase 326 - Alfresco People Site Request Withdraw Verification

Date: 2026-03-19

## Commands

- `cd ecm-core && mvn -q -DskipTests compile`
- `cd ecm-core && mvn -q -Dtest=WorkflowControllerTest,PeopleControllerTest,PeopleControllerSecurityTest test`
- `cd ecm-frontend && npx eslint --max-warnings=0 src/pages/WorkflowProcessesPage.tsx src/pages/TasksPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts src/services/peopleService.ts`
- `cd ecm-frontend && npm run -s build`
- `git diff --check -- ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java ecm-frontend/src/pages/WorkflowProcessesPage.tsx ecm-frontend/src/pages/TasksPage.tsx ecm-frontend/src/pages/PeopleDirectoryPage.tsx ecm-frontend/src/services/workflowService.ts ecm-frontend/src/services/peopleService.ts`

## Verification Notes

- people site-request withdrawal compiles and passes focused controller/security coverage
- the writable site-request list passes lint and production build packaging
- the working tree for this batch is whitespace-clean
