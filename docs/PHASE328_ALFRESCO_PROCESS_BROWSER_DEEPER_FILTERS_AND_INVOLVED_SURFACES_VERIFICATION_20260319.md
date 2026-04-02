# Phase 328 Verification - Alfresco Process Browser Deeper Filters And Involved Surfaces

## Verified Commands

```bash
cd ecm-core && mvn -q -DskipTests compile
cd ecm-core && mvn -q -Dtest=WorkflowControllerTest,PeopleControllerTest,PeopleControllerSecurityTest test
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts src/services/peopleService.ts
cd ecm-frontend && npm run -s build
```

## Verified Outcomes

- workflow controller tests pass for:
  - richer process browser filters
  - involved actor resources
- backend compile passes with the updated process-browser service/controller contract
- frontend lint passes with the new dialog/actions
- frontend production build succeeds
