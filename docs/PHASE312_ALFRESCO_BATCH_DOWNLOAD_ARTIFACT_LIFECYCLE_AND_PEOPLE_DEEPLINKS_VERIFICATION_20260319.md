# Phase 312 - Alfresco Batch Download Artifact Lifecycle and People Deeplinks Verification

Date: 2026-03-19

## Verified Scope

- async batch download lifecycle metadata
- single-task cleanup for terminal download tasks
- People Directory preview/discussion deeplinks for favorites and mentioned comments
- comment DTO node metadata for user-scoped comment pages

## Commands

### Backend

```bash
cd ecm-core
mvn -q -Dtest=WorkflowControllerTest,BatchDownloadControllerTest,BatchDownloadAsyncTaskRegistryTest,CommentControllerTest test
mvn -q -DskipTests compile
```

Result:

- Passed

### Frontend

```bash
cd ecm-frontend
npx eslint --max-warnings=0 src/services/workflowService.ts src/pages/TasksPage.tsx src/services/nodeService.ts src/pages/AdminDashboard.tsx src/services/commentService.ts src/pages/PeopleDirectoryPage.tsx
npm run -s build
```

Result:

- Passed

## Manual Verification Expectations

- Admin Dashboard batch download rows show archive size and retention expiry where applicable
- Terminal batch download tasks can be cleaned individually from the admin task table
- People Directory favorites can preview or jump straight into discussion without leaving the page
- Mentioned comments in People Directory show their source document and can open that document preview/discussion

## Files Verified

- [BatchDownloadAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java)
- [BatchDownloadController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java)
- [BatchDownloadControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java)
- [BatchDownloadAsyncTaskRegistryTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistryTest.java)
- [CommentDto.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/dto/CommentDto.java)
- [CommentControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/CommentControllerTest.java)
- [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts)
- [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx)
- [commentService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/commentService.ts)
- [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx)
