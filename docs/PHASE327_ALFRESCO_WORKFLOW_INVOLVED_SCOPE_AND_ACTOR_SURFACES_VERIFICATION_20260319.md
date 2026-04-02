# Phase 327 Verification - Alfresco Workflow Involved Scope And Actor Surfaces

## Verified Commands

```bash
cd ecm-core && mvn -q -DskipTests compile
cd ecm-core && mvn -q -Dtest=WorkflowControllerTest,PeopleControllerTest,PeopleControllerSecurityTest test
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts src/services/peopleService.ts
cd ecm-frontend && npm run -s build
```

## Verified Outcomes

- backend compiles with new workflow filter and involved-actor contracts
- workflow controller tests pass with:
  - involved inbox scope
  - richer process browser filters
  - process/task involved actor resources
- frontend lint passes for updated workflow surfaces
- frontend production build succeeds

## Notes

- The backend test command also covered people tests because Phase 329 shipped in the same batch.
