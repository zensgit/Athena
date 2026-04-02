# Phase 344 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/TasksPage.tsx ecm-frontend/src/pages/WorkflowProcessesPage.tsx ecm-frontend/src/services/workflowService.ts
```

## Result

- workflow pages eslint passed
- frontend production build passed
- scoped `git diff --check` passed

## Notes

- 本阶段未新增 backend contract，全部是前端 workflow workbench 的过滤/治理增强。
