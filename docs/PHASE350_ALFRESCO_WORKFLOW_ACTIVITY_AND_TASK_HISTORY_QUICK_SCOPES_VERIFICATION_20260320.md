# Phase 350 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/TasksPage.tsx ecm-frontend/src/pages/WorkflowProcessesPage.tsx
```

## Result

- workflow pages eslint passed
- frontend production build passed
- scoped `git diff --check` passed

## Notes

- 本阶段不新增 backend contract，全部是 workflow activity/task-history 的 quick scopes 增强。
