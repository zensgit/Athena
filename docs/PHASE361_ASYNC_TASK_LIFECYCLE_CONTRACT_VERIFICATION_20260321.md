# Phase 361 Verification: Async Task Lifecycle Contract

## Date
- 2026-03-21

## Commands
```bash
cd ecm-core && mvn -q -Dtest=AsyncTaskGovernanceServiceTest,AsyncTaskSummaryAdaptersTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest,SearchControllerTest test
```

```bash
git diff --check -- ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskSummaryAdapters.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceConfiguration.java \
  ecm-core/src/main/java/com/ecm/core/controller/SearchController.java \
  ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java \
  ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java \
  ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskSummaryAdaptersTest.java
```

## Result
- Targeted Maven tests passed.
- `git diff --check` passed for the Phase 361 slice.

## Coverage
- `AsyncTaskSummaryAdaptersTest`
  - verifies shared snapshot to legacy summary DTO mapping for `search`, `preview`, and `ops`
- `AsyncTaskGovernanceServiceTest`
  - verifies governance still aggregates the shared snapshot contract correctly
- `AnalyticsControllerTest`
  - verifies async governance overview mapping remains stable after the shared contract convergence
- `AnalyticsControllerSecurityTest`
  - verifies async governance overview security remains intact
- `SearchControllerTest`
  - recompiles and exercises the search controller after the summary endpoint refactor
