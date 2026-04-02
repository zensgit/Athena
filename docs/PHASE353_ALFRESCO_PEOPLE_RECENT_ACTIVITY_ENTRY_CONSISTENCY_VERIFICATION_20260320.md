# Phase 353 - Verification

## Commands

```bash
cd ecm-frontend && npx eslint --max-warnings=0 src/pages/TasksPage.tsx src/pages/WorkflowProcessesPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/workflowService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/TasksPage.tsx ecm-frontend/src/pages/WorkflowProcessesPage.tsx ecm-frontend/src/pages/PeopleDirectoryPage.tsx docs/PHASE352_ALFRESCO_WORKFLOW_ACTIVITY_FILTER_AND_STATS_SURFACES_DEV_20260320.md docs/PHASE352_ALFRESCO_WORKFLOW_ACTIVITY_FILTER_AND_STATS_SURFACES_VERIFICATION_20260320.md docs/PHASE353_ALFRESCO_PEOPLE_RECENT_ACTIVITY_ENTRY_CONSISTENCY_DEV_20260320.md docs/PHASE353_ALFRESCO_PEOPLE_RECENT_ACTIVITY_ENTRY_CONSISTENCY_VERIFICATION_20260320.md docs/DOCS_INDEX_20260212.md docs/RELEASE_NOTES_20260217.md
```

## Result

- people/workflow scoped eslint passed
- frontend production build passed
- scoped `git diff --check` passed

## Notes

- 本阶段无后端改动。
- build 中只有既有 bundle size 提示，没有失败。
