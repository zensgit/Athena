# Phase 348 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/TasksPage.tsx ecm-frontend/src/pages/WorkflowProcessesPage.tsx ecm-frontend/src/pages/PeopleDirectoryPage.tsx
```

## Result

- workflow pages eslint passed
- frontend production build passed
- scoped `git diff --check` passed

## Notes

- 本阶段不新增 backend contract，全部是 workflow workbench 的本地 quick scopes 与统计增强。
