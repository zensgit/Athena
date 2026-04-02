# Phase 336 - Verification

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
- workflow-related frontend eslint passed
- frontend production build passed

## Notes

- 本轮 task history 主要做 controller/service/resource 和页面消费闭环，没有补跑浏览器 e2e。
