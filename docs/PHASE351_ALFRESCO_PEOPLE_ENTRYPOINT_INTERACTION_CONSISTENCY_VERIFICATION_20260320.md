# Phase 351 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/TasksPage.tsx ecm-frontend/src/pages/WorkflowProcessesPage.tsx ecm-frontend/src/pages/PeopleDirectoryPage.tsx docs/PHASE350_ALFRESCO_WORKFLOW_ACTIVITY_AND_TASK_HISTORY_QUICK_SCOPES_DEV_20260320.md docs/PHASE350_ALFRESCO_WORKFLOW_ACTIVITY_AND_TASK_HISTORY_QUICK_SCOPES_VERIFICATION_20260320.md docs/PHASE351_ALFRESCO_PEOPLE_ENTRYPOINT_INTERACTION_CONSISTENCY_DEV_20260320.md docs/PHASE351_ALFRESCO_PEOPLE_ENTRYPOINT_INTERACTION_CONSISTENCY_VERIFICATION_20260320.md docs/DOCS_INDEX_20260212.md docs/RELEASE_NOTES_20260217.md
```

## Result

- people/workflow scoped eslint passed
- frontend production build passed
- scoped `git diff --check` passed

## Notes

- 本阶段无后端改动。
- build 中仍有既有 bundle size 提示，但构建成功，不影响交付。
