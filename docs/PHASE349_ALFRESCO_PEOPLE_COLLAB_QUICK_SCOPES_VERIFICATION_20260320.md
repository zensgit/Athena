# Phase 349 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/TasksPage.tsx ecm-frontend/src/pages/WorkflowProcessesPage.tsx ecm-frontend/src/pages/PeopleDirectoryPage.tsx
```

## Result

- people/workflow scoped eslint passed
- frontend production build passed
- scoped `git diff --check` passed

## Notes

- 本阶段不新增 backend 接口，全部是 People Directory 协作区块的 quick scopes 增强。
