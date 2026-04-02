# Phase 360 Verification: Async Task Provider Registry

## Date
- 2026-03-21

## Commands
```bash
cd ecm-core && mvn -q -Dtest=AsyncTaskGovernanceServiceTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest test
```

```bash
git diff --check -- ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java \
  ecm-core/src/main/java/com/ecm/core/service/AuditExportAsyncTaskRegistry.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceConfiguration.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceService.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceProviderRegistry.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceProvider.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceOverviewSnapshot.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceDomainSnapshot.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceRiskLevel.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceStatus.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/SimpleAsyncTaskGovernanceProvider.java \
  ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskSummaryAdapters.java \
  ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerSecurityTest.java \
  ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskGovernanceServiceTest.java
```

## Result
- `mvn` targeted tests passed.
- `git diff --check` passed.

## Coverage
- `AnalyticsControllerTest`
  - audit async export endpoints still behave against a real `AuditExportAsyncTaskRegistry`
  - async governance overview still returns the existing response shape after the controller slimming
- `AnalyticsControllerSecurityTest`
  - `/api/v1/analytics/async-governance/overview` still requires authentication and `ADMIN`
- `AsyncTaskGovernanceServiceTest`
  - provider registry order is respected
  - cross-domain summaries aggregate into one overview snapshot
  - failing providers degrade only their own domain and escalate overall status/risk
  - batch download `cancelRequested` work is still normalized as active running work
