# Phase 316 - Alfresco Download Admin Query Filter and People Profile Deeplinks Verification

Date: 2026-03-19

## Verified Scope

- batch download async query filtering
- batch download admin query UI
- People Directory query-param preselection
- favorites creator profile deeplinks

## Commands

### Backend

```bash
cd ecm-core
mvn -q -Dtest=WorkflowControllerTest,BatchDownloadControllerTest,BatchDownloadAsyncTaskRegistryTest test
mvn -q -DskipTests compile
```

Result:

- Passed

### Frontend

```bash
cd ecm-frontend
npx eslint --max-warnings=0 src/services/nodeService.ts src/pages/AdminDashboard.tsx src/services/workflowService.ts src/pages/TasksPage.tsx src/pages/FavoritesPage.tsx src/pages/PeopleDirectoryPage.tsx
npm run -s build
```

Result:

- Passed

## Manual Verification Expectations

- batch download admin can locate tasks by partial task ID or filename
- changing query resets pagination to the first page
- auto-refresh keeps the same batch download query active
- `/people-directory?username=alice` opens Alice directly
- favorites rows with creators can jump to the matching People Directory profile

## Files Verified

- [BatchDownloadAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java)
- [BatchDownloadController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java)
- [BatchDownloadControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java)
- [BatchDownloadAsyncTaskRegistryTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistryTest.java)
- [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts)
- [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx)
- [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx)
- [FavoritesPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/FavoritesPage.tsx)
