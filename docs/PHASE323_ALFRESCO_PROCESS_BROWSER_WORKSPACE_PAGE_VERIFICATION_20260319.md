# Phase 323 - Alfresco Process Browser Workspace Page Verification

Date: 2026-03-19

## Commands

- `cd ecm-frontend && npx eslint --max-warnings=0 src/pages/WorkflowProcessesPage.tsx src/pages/TasksPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/peopleService.ts src/components/layout/MainLayout.tsx src/components/layout/MainLayout.menu.test.tsx src/App.tsx`
- `cd ecm-frontend && CI=true npm test -- --runTestsByPath src/components/layout/MainLayout.menu.test.tsx --watchAll=false`
- `cd ecm-frontend && npm run -s build`

## Verification Notes

- the dedicated workflow process browser route compiles and passes lint
- account-menu navigation exposes the new `Workflow Processes` entry
- the production build includes the new process browser workspace without type or bundling regressions
