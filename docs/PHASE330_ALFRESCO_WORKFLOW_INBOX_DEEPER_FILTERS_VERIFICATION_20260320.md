# Phase 330 Verification - Alfresco Workflow Inbox Deeper Filters

## Verified Commands

```bash
cd ecm-core && mvn -q -Dtest=WorkflowControllerTest,PeopleControllerTest,PeopleControllerSecurityTest test
cd ecm-core && mvn -q -DskipTests compile
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/PeopleDirectoryPage.tsx src/pages/TasksPage.tsx src/services/workflowService.ts src/services/peopleService.ts
cd ecm-frontend && npm run -s build
```

## Verified Outcomes

- workflow controller tests pass for:
  - `processId` task inbox filtering
  - legacy `q` task inbox filtering
  - `candidateGroup` task inbox filtering
  - involved scope compatibility with new filter contract
- backend compile succeeds with the richer workflow inbox controller/service contract
- frontend lint passes with the new task inbox filter controls
- frontend production build succeeds
