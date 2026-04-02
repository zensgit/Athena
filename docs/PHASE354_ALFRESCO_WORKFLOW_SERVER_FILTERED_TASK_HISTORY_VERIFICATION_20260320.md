# Phase 354 - Verification

## Commands

```bash
cd ecm-core && mvn -q -Dtest=WorkflowControllerTest test
cd ecm-core && mvn -q -DskipTests compile
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
```

## Result

- `WorkflowControllerTest` passed
- backend compile passed
- workflow scoped eslint passed
- frontend production build passed

## Notes

- `task-history` 的服务端过滤与前端 quick scopes 为叠加关系，不会破坏既有本地筛查体验。
- `TasksPage` 已把 detail refresh token 纳入依赖，process variable save/delete 后会重新拉取当前 detail。
