# Phase 333 - Verification

## Commands

```bash
cd ecm-core && mvn -q -Dtest=WorkflowControllerTest test
cd ecm-core && mvn -q -DskipTests compile
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/WorkflowProcessesPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
```

## Result

- `WorkflowControllerTest` passed
- backend compile passed
- frontend eslint passed
- frontend production build passed

## Notes

- `WorkflowProcessesPage` 的通用启动入口已经接到主线，验证覆盖了 controller 契约和前端构建闭环。
