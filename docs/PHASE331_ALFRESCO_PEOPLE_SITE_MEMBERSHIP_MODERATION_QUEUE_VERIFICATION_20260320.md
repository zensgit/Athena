# Phase 331 Verification - Alfresco People Site Membership Moderation Queue

## Verified Commands

```bash
cd ecm-core && mvn -q -Dtest=WorkflowControllerTest,PeopleControllerTest,PeopleControllerSecurityTest test
cd ecm-core && mvn -q -DskipTests compile
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/PeopleDirectoryPage.tsx src/pages/TasksPage.tsx src/services/workflowService.ts src/services/peopleService.ts
cd ecm-frontend && npm run -s build
```

## Verified Outcomes

- people controller tests pass for:
  - visible moderation queue listing
  - approve request flow
  - reject request flow
  - create/update/withdraw compatibility
- people controller security tests pass for:
  - non-admin moderation denial
  - authenticated self-service request management
- frontend lint passes for the moderation queue panel and people service updates
- frontend production build succeeds
