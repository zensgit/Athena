# Phase 324 - Alfresco People Self-Service Profile and Preferences Verification

Date: 2026-03-19

## Commands

- `cd ecm-core && mvn -q -DskipTests compile`
- `cd ecm-core && mvn -q -Dtest=PeopleControllerTest,PeopleControllerSecurityTest test`
- `cd ecm-frontend && npx eslint --max-warnings=0 src/pages/WorkflowProcessesPage.tsx src/pages/TasksPage.tsx src/pages/PeopleDirectoryPage.tsx src/services/peopleService.ts src/components/layout/MainLayout.tsx src/components/layout/MainLayout.menu.test.tsx src/App.tsx`
- `cd ecm-frontend && npm run -s build`

## Verification Notes

- people self-service profile and preference endpoints compile and pass focused controller/security coverage
- the people-directory self-service dialogs pass lint and bundle cleanly with the wider collaboration workspace
- the favorite-removal and profile-edit surfaces ship on the same build used by the new process browser workspace
