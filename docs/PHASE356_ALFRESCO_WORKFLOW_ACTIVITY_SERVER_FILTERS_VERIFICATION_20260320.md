# Phase 356 - Verification

## Commands

```bash
cd ecm-core && mvn -Dtest=WorkflowControllerTest test
cd ecm-frontend && npx eslint src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
```

## Result

- `WorkflowControllerTest` passed (`30` tests, `0` failures)
- frontend `eslint` passed for the touched workflow files
- frontend production build passed

## Notes

- backend verification initially exposed pre-existing `WorkflowService` record constructor drift; this phase includes the minimal fixes required to restore compilation
- activity quick scopes remain local filters layered on top of the server-filtered activity dataset
