# Phase 361B Verification: Async Task Lifecycle Action Contract

## Date
- 2026-03-21

## Commands
```bash
cd ecm-core && mvn -q -Dtest=AsyncTaskLifecycleServiceTest,AsyncTaskGovernanceServiceTest,AsyncTaskSummaryAdaptersTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest test
```

```bash
git diff --check -- ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskActionSnapshot.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskStatusSnapshot.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleListSnapshot.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleAdapters.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleService.java \
  ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java \
  ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskLifecycleServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerSecurityTest.java \
  docs/PHASE361B_ASYNC_TASK_LIFECYCLE_ACTION_CONTRACT_DEV_20260321.md \
  docs/PHASE361B_ASYNC_TASK_LIFECYCLE_ACTION_CONTRACT_VERIFICATION_20260321.md
```

## Result
- Targeted Maven tests passed.
- `git diff --check` passed for the Phase 361B slice.

## Coverage
- `AsyncTaskLifecycleServiceTest`
  - verifies cross-domain merge, recency sort, paging, and action affordance normalization
  - verifies batch domain alias handling and filtered totals
  - verifies unsupported batch status filters fail fast with `400`
- `AnalyticsControllerTest`
  - verifies `/api/v1/analytics/async-governance/tasks` maps the shared lifecycle snapshot into the public response shape
- `AnalyticsControllerSecurityTest`
  - verifies the new recent-task endpoint still requires authentication and `ADMIN`
- `AsyncTaskGovernanceServiceTest`
  - verifies the existing overview path remains stable alongside the new lifecycle feed
- `AsyncTaskSummaryAdaptersTest`
  - verifies the shared summary contract remains intact for legacy task-center summary DTOs
