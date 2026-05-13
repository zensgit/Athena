# Property Encryption Async Governance Integration Manifest

Date: 2026-05-12

## Context

This manifest groups the full 2026-05-12 Property Encryption async-governance
addendum for review, commit, and push. It is intentionally separate from the
design documents: its purpose is to prevent the final bundle from becoming an
unclear pile of backend, frontend, E2E, scripts, and closeout-document changes.

Pre-existing local state:

- `.env` is modified locally and must not be staged.

## Bundle Summary

The addendum does four things:

1. Adds a `propertyEncryption` domain to shared async-governance backend
   contracts.
2. Adds Admin Dashboard and Property Encryption page UI support for that domain.
3. Adds mocked E2E coverage for direct navigation, task filtering, cancel
   action, and overview fallback.
4. Adds local/closeout gate scripting plus final docs/runbook updates.

## Suggested Commit Split

### Commit 1: Backend Async Governance Domain

Suggested message:

```text
feat(core): expose property encryption jobs in async governance
```

Files:

- `ecm-core/src/main/java/com/ecm/core/asynctask/PropertyEncryptionAsyncTaskService.java`
- `ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceConfiguration.java`
- `ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleAdapters.java`
- `ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepository.java`
- `ecm-core/src/main/java/com/ecm/core/repository/PropertyEncryptionRewrapJobRepository.java`
- `ecm-core/src/test/java/com/ecm/core/asynctask/PropertyEncryptionAsyncTaskServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskGovernanceServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskLifecycleServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`

Verification anchor:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=PropertyEncryptionAsyncTaskServiceTest,AsyncTaskGovernanceServiceTest,AsyncTaskLifecycleServiceTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest \
  test
```

Expected result:

```text
Tests run: 65, Failures: 0, Errors: 0, Skipped: 0
```

### Commit 2: Frontend Async Governance UI

Suggested message:

```text
feat(frontend): add property encryption async governance UI
```

Files:

- `ecm-frontend/src/pages/AdminDashboard.tsx`
- `ecm-frontend/src/pages/PropertyEncryptionOperationsPage.tsx`
- `ecm-frontend/src/pages/PropertyEncryptionOperationsPage.test.tsx`
- `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`
- `ecm-frontend/e2e/admin-property-encryption.mock.spec.ts`
- `ecm-frontend/e2e/admin-async-governance-overview-fallback.mock.spec.ts`
- `scripts/phase5-regression.sh`

Verification anchors:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/pages/PropertyEncryptionOperationsPage.test.tsx --watchAll=false
npm run lint
CI=true npm run build
```

Expected result:

```text
Jest: 1 suite passed, 2 tests passed
Lint: passed
Build: compiled successfully
```

Mocked E2E is covered by the one-command gate in Commit 3.

### Commit 3: Addendum Gate Script

Suggested message:

```text
test(property-encryption): add async governance addendum gate
```

Files:

- `scripts/property-encryption-async-governance-gate.sh`
- `scripts/property-encryption-closeout-preflight.sh`

Verification anchor:

```bash
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
scripts/property-encryption-async-governance-gate.sh
```

Expected result:

```text
Backend async-governance contract tests: 65 tests, 0 failures, 0 errors
Frontend targeted Jest: 1 suite passed, 2 tests passed
Frontend lint: passed
Frontend production build: compiled successfully
Phase 5 registry-only preflight: expected events 24, observed markers 24
Mocked E2E: 3 passed
property_encryption_async_governance_gate: ok
```

### Commit 4: Documentation And Handoff

Suggested message:

```text
docs(property-encryption): document async governance addendum
```

Files:

- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_DESIGN_VERIFICATION_20260512.md`
- `docs/PROPERTY_ENCRYPTION_CLOSEOUT_GATE_ASYNC_GOVERNANCE_VERIFICATION_20260512.md`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_OVERVIEW_FALLBACK_E2E_VERIFICATION_20260512.md`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_GATE_SCRIPT_DESIGN_VERIFICATION_20260512.md`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_CLOSEOUT_DOCS_ADDENDUM_20260512.md`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_HANDOFF_RUNBOOK_20260512.md`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_INTEGRATION_MANIFEST_20260512.md`
- `docs/PROPERTY_ENCRYPTION_FINAL_ACCEPTANCE_MATRIX_20260505.md`
- `docs/PROPERTY_ENCRYPTION_CLOSEOUT_TODO_20260505.md`

Verification anchor:

```bash
git diff --check -- . ':!.env'
```

Expected result:

```text
passed
```

## Pre-Push Checklist

Run this immediately before push:

```bash
git status --short --branch
git diff --check -- . ':!.env'
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
scripts/property-encryption-async-governance-gate.sh
```

Do not stage `.env`.

## Post-Push Checklist

Watch these GitHub Actions jobs:

- `Backend Verify`
- `Frontend Build & Test`
- `Property Encryption Closeout Gate`
- `Phase 5 Mocked Regression Gate`

If all are green, append the run ID and job evidence under:

- `docs/PROPERTY_ENCRYPTION_FINAL_ACCEPTANCE_MATRIX_20260505.md`
  `2026-05-12 Async Governance Addendum`

If one is red, use:

- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_HANDOFF_RUNBOOK_20260512.md`

## Verification

This manifest was created after these local checks had passed:

```text
scripts/property-encryption-async-governance-gate.sh full path: ok
Backend async-governance suite: 65/65
Frontend Jest: 2/2
Frontend lint: passed
Frontend production build: compiled successfully
Phase 5 registry-only: 24/24 markers matched
Mocked E2E: 3/3
git diff --check -- . ':!.env': passed
```

## Remaining Work

- Stage and commit the bundle in the suggested split, excluding `.env`.
- Push and record fresh CI evidence in the final acceptance matrix addendum.
