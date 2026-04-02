# Phase 340 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/WorkflowProcessesPage.tsx src/pages/TasksPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
```

## Result

- workflow history filter surfaces eslint passed
- frontend production build passed

## Notes

- 本阶段不新增后端接口，仅在既有 history relation 上扩展前端过滤体验。
