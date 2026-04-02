# Phase 352 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/TasksPage.tsx ecm-frontend/src/pages/WorkflowProcessesPage.tsx ecm-frontend/src/pages/PeopleDirectoryPage.tsx
```

## Result

- workflow/pages scoped eslint passed
- frontend production build passed
- scoped `git diff --check` passed

## Notes

- 本阶段无后端改动。
- build 仍有既有 bundle size 提示，但构建成功。
