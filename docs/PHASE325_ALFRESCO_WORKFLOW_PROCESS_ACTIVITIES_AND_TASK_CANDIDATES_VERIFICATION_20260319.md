# Phase 325 - Alfresco Workflow Process Activities and Task Candidates Verification

Date: 2026-03-19

## Commands

- `cd ecm-core && mvn -q -DskipTests compile`
- `cd ecm-core && mvn -q -Dtest=WorkflowControllerTest,PeopleControllerTest,PeopleControllerSecurityTest test`
- `cd ecm-frontend && npx eslint --max-warnings=0 src/pages/WorkflowProcessesPage.tsx src/pages/TasksPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts src/services/peopleService.ts`
- `cd ecm-frontend && npm run -s build`

## Verification Notes

- workflow backend compiles with the new process-activity and task-candidate resources
- focused controller tests cover the new workflow lifecycle endpoints
- the workbench and process-browser lifecycle panels pass lint and production build packaging
