# Phase 337 - Verification

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
- process browser / service eslint passed
- frontend production build passed

## Notes

- 变量写能力目前集中在 process browser workspace，任务页暂未重复铺设相同编辑控件。
