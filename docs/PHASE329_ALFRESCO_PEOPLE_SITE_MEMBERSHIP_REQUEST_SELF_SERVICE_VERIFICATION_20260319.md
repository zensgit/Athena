# Phase 329 Verification - Alfresco People Site Membership Request Self Service

## Verified Commands

```bash
cd ecm-core && mvn -q -DskipTests compile
cd ecm-core && mvn -q -Dtest=WorkflowControllerTest,PeopleControllerTest,PeopleControllerSecurityTest test
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts src/services/peopleService.ts
cd ecm-frontend && npm run -s build
```

## Verified Outcomes

- people controller tests pass for:
  - create membership request
  - update membership request
  - withdraw membership request
- people controller security tests pass for authenticated self-service access
- frontend lint passes with the new request dialog/actions
- frontend production build succeeds

## Manual Spot-Checks Suggested

- open People Directory as current user
- create a new membership request
- edit the request message/role
- withdraw the request
- confirm the list refreshes after each action
