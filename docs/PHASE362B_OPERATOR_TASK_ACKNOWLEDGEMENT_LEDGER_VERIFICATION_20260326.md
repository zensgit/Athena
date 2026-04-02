# Phase 362B Verification

## Scope

Verify persistent operator acknowledgement ledger support for the shared async governance task feed.

## Verified Commands

### Backend

```bash
cd ecm-core && mvn -q -Dtest=AsyncTaskAcknowledgementServiceTest,AsyncTaskLifecycleServiceTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest test
```

Result:

- passed
- covered acknowledgement projection
- covered hide/show filtering in lifecycle service
- covered analytics controller list / acknowledge / unacknowledge endpoints
- covered admin-role protection for the new acknowledge endpoint

### Frontend lint

```bash
cd ecm-frontend && npx eslint src/pages/AdminDashboard.tsx
```

Result:

- passed

### Frontend production build

```bash
cd ecm-frontend && npm run -s build
```

Result:

- passed

Note:

- CRA still reports the existing bundle-size warning
- no new TypeScript or lint regressions were introduced by this phase

### Patch hygiene

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/asynctask \
  ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java \
  ecm-core/src/main/java/com/ecm/core/entity/AsyncTaskAcknowledgement.java \
  ecm-core/src/main/java/com/ecm/core/repository/AsyncTaskAcknowledgementRepository.java \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml \
  ecm-core/src/main/resources/db/changelog/changes/032-add-async-task-acknowledgements.xml \
  ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskAcknowledgementServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskLifecycleServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerSecurityTest.java \
  ecm-frontend/src/pages/AdminDashboard.tsx
```

Result:

- passed

## Manual Behavioral Expectations

- recent async task list hides acknowledged items by default
- enabling `Show acknowledged` reveals restored noise history for the current operator
- terminal tasks expose `Acknowledge`
- acknowledged tasks expose `Restore`
- acknowledgement state persists per user through the new ledger table
- recent-task API returns `fingerprint`, `acknowledged`, and `acknowledgedAt`

## Remaining Risk

- `includeAcknowledged=false` still operates on the recent task window rather than a fully materialized infinite ledger; for the current admin use case this is acceptable, but a larger dedicated operator inbox may eventually need first-class acknowledged counts and pagination semantics
- this phase improves operator detail for async work, but rendition state is still backed by preview-derived document fields rather than a first-class rendition resource model
