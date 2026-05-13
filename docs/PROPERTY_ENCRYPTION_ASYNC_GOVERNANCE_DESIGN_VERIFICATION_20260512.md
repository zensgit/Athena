# Property Encryption Async Governance - Design and Verification

Date: 2026-05-12

## Context

Property Encryption already had durable backfill and rewrap job ledgers, but
those jobs were only visible on the dedicated Property Encryption admin page.
The unified async task governance surface covered audit, ops recovery, search,
preview, and batch download, so property-encryption operations were still
outside the cross-domain task center.

This slice connects existing property-encryption jobs to the shared async
governance contract without changing job execution, persistence, or database
schema.

## Design

Added a `propertyEncryption` async governance domain with label
`Property Encryption`.

The domain aggregates both ledgers:

| Ledger | Task id prefix | Source table |
| --- | --- | --- |
| Backfill | `backfill:<jobId>` | `property_encryption_backfill_jobs` |
| Rewrap | `rewrap:<jobId>` | `property_encryption_rewrap_jobs` |

Status normalization:

| Property Encryption status | Async governance status |
| --- | --- |
| `PLANNED` | `QUEUED` |
| `RUNNING` | `RUNNING` |
| `CANCEL_REQUESTED` | `RUNNING` |
| `SUCCEEDED` | `COMPLETED` |
| `FAILED` | `FAILED` |
| `CANCELLED` | `CANCELLED` |

Action exposure is intentionally narrow:

- `PLANNED` and `RUNNING` jobs expose the existing cancel endpoint.
- `CANCEL_REQUESTED` jobs remain active/running but do not expose a second
  cancel action.
- No download or cleanup action is exposed because the property-encryption job
  contract does not currently support those operations.

The existing `/api/v1/analytics/async-governance/overview` response now includes
the property-encryption domain through the provider registry. The existing
`/api/v1/analytics/async-governance/tasks` response can filter it with
`domain=propertyencryption`, `domain=property-encryption`, or
`domain=property_encryption`.

Controller-level JSON contract coverage now pins the property-encryption task
list response, including alias forwarding, `backfill:<jobId>` task ids,
operator acknowledgement metadata, and the cancel URL shape exposed to the
frontend.

Frontend changes are limited to the existing Admin Dashboard async governance
surfaces:

- the Async Task Health Overview table now includes `Property Encryption` when
  the unified overview endpoint is available;
- the Recent Async Tasks domain dropdown now includes `Property Encryption`;
- `/admin?asyncTaskDomain=propertyencryption` deep-links the Admin Dashboard
  directly into the Property Encryption Recent Async Tasks filter;
- Recent Async Tasks domain/status/include-acknowledged filters synchronize
  back to `asyncTaskDomain`, `asyncTaskStatus`, and
  `asyncTaskIncludeAcknowledged` URL parameters so filtered governance views can
  be shared or refreshed without losing context;
- success status coloring also tolerates `SUCCEEDED` even though the backend
  returns `COMPLETED` for this domain.

The dedicated Property Encryption operations page now exposes an
`Open in Async Governance` operator bridge to that filtered dashboard view. This
keeps the page focused on job planning/execution while making cross-domain
governance discoverable after a backfill or rewrap job is planned.

The Admin Dashboard Recent Async Tasks action path can invoke the exposed
Property Encryption cancel URL. The URL is normalized from `/api/v1/...` to the
shared frontend API client path before posting, matching the other async task
domains. After cancellation, the refreshed task row remains visible as active
`RUNNING` but no longer exposes duplicate cancel because backend
`CANCEL_REQUESTED` maps into the shared active/running bucket.

Property Encryption has no standalone legacy health-summary endpoint. If the
unified overview endpoint fails and the UI falls back to per-domain legacy
summary calls, the Property Encryption row is marked degraded with
`overview-required` instead of inventing an empty healthy summary.

## Verification

### Backend

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=PropertyEncryptionAsyncTaskServiceTest,AsyncTaskGovernanceServiceTest,AsyncTaskLifecycleServiceTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest \
  test
```

Result:

```text
Tests run: 65, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Covered scenarios:

- Backfill and rewrap counts aggregate into queued/running/completed/cancelled/failed buckets.
- Recent task snapshots use `backfill:` and `rewrap:` task ids.
- Running backfill jobs expose cancel affordance.
- Cancel-requested rewrap jobs stay active but do not expose duplicate cancel.
- Unknown property-encryption status filters return HTTP 400.
- The unified lifecycle service accepts the `property-encryption` domain alias.
- `GET /api/v1/analytics/async-governance/tasks?domain=property-encryption`
  returns the normalized property-encryption domain, `backfill:<jobId>` task id,
  acknowledgement fields, and the admin cancel URL contract.
- Existing analytics controller and security tests continue to pass.

### Frontend

Command:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/pages/PropertyEncryptionOperationsPage.test.tsx --watchAll=false
```

Result:

```text
Test Suites: 1 passed, 1 total
Tests:       2 passed, 2 total
```

Covered scenarios:

- The Property Encryption operations page renders the operator bridge to
  `/admin?asyncTaskDomain=propertyencryption`.
- Existing dry-run, plan, run, and cancel flows still work.

Command:

```bash
cd ecm-frontend
(npx serve -s build -l 5510 >/tmp/athena-property-encryption-async-governance-serve.log 2>&1 & echo $! >/tmp/athena-property-encryption-async-governance-serve.pid)
ECM_UI_URL=http://localhost:5510 npx playwright test \
  e2e/admin-property-encryption.mock.spec.ts \
  e2e/admin-audit-filter-export.mock.spec.ts \
  --project=chromium
kill "$(cat /tmp/athena-property-encryption-async-governance-serve.pid)"
```

Result:

```text
2 passed
```

Covered scenarios:

- The Property Encryption operations page exposes the governance bridge link.
- The Admin Dashboard honors `asyncTaskDomain=propertyencryption`.
- Recent Async Tasks status and include-acknowledged filter changes update both
  the URL and the `/analytics/async-governance/tasks` query.
- The Recent Async Tasks table renders a Property Encryption backfill task with
  a cancel action, posts to
  `/admin/property-encryption/backfill-jobs/{id}/cancel`, and suppresses a
  duplicate cancel action after the backend reports cancellation requested.
- Admin Dashboard consumes the unified async governance overview endpoint.
- The Async Task Health Overview table renders the `Property Encryption`
  domain row with real active/terminal/task-count values.
- Refresh reloads the unified overview endpoint instead of dropping back to
  legacy per-domain summary calls.

Command:

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

Command:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: compiled successfully.

Notes:

- The build still prints the existing CRA `fs.F_OK` deprecation warning.
- The build still prints the existing bundle-size advisory.

## Files Changed

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
- `ecm-frontend/src/pages/PropertyEncryptionOperationsPage.tsx`
- `ecm-frontend/src/pages/PropertyEncryptionOperationsPage.test.tsx`
- `ecm-frontend/src/pages/AdminDashboard.tsx`
- `ecm-frontend/e2e/admin-property-encryption.mock.spec.ts`
- `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`

## Remaining Work

- No additional development is required for this governance slice.
- If operators later need property-encryption cleanup or artifact downloads,
  add those operations to the property-encryption job contract first, then expose
  them through the shared async action snapshot.
- `.env` has pre-existing local changes and remains intentionally excluded.
