# Property Encryption Service Shape Guards: Design and Verification

Date: 2026-05-18

## Scope

This slice hardens `ecm-frontend/src/services/propertyEncryptionService.ts`
against malformed runtime responses while preserving endpoint paths,
request payloads, query params, method names, and admin-only backend
semantics.

No backend code, UI page, route contract, package file, or migration was
changed. `.env` was already modified before this slice and was not
touched, staged, or committed.

## Backend Contract

Backend sources checked:

- `ecm-core/src/main/java/com/ecm/core/controller/PropertyEncryptionOperationsController.java`
- `ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionOperationsService.java`
- `ecm-core/src/main/java/com/ecm/core/entity/PropertyEncryptionRewrapJob.java`
- `ecm-core/src/main/java/com/ecm/core/entity/PropertyEncryptionBackfillJob.java`

Controller mount:

- `@RequestMapping("/api/v1/admin/property-encryption")`

Frontend relative paths remain unchanged:

- `GET /admin/property-encryption/status`
- `GET /admin/property-encryption/definitions`
- `POST /admin/property-encryption/rewrap-jobs/dry-run`
- `POST /admin/property-encryption/backfill-jobs/dry-run`
- `POST /admin/property-encryption/rewrap-jobs/plan`
- `GET /admin/property-encryption/rewrap-jobs`
- `POST /admin/property-encryption/rewrap-jobs/{jobId}/run`
- `POST /admin/property-encryption/rewrap-jobs/{jobId}/cancel`
- `POST /admin/property-encryption/backfill-jobs/plan`
- `GET /admin/property-encryption/backfill-jobs`
- `POST /admin/property-encryption/backfill-jobs/{jobId}/run`
- `POST /admin/property-encryption/backfill-jobs/{jobId}/cancel`

Closed job status values:

- `PLANNED | RUNNING | SUCCEEDED | FAILED | CANCEL_REQUESTED | CANCELLED`

## Design

Added the exported sentinel:

- `PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE`

Added runtime guards for:

- `PropertyEncryptionStatus`
- `EncryptedPropertyDefinitionSummary[]`
- `PropertyEncryptionRewrapDryRunResult`
- `PropertyEncryptionBackfillDryRunResult`
- `PropertyEncryptionRewrapJobDto`
- `PropertyEncryptionBackfillJobDto`
- nested key-version and definition-count snapshots

Guard policy:

- Reject HTML fallback and non-object JSON where objects are expected.
- Require count fields to be finite numbers.
- Require boolean capability and job execution fields to serialize as
  booleans.
- Require warnings and missing source key versions to be `string[]`.
- Allow backend nullable text fields such as `activeKeyVersion`,
  `targetKeyVersion`, `title`, `ownerQName`, timestamps, and `lastError`.
- Require job status to match the backend closed enum values.
- Guard nested dry-run and job ledger arrays before returning data to UI
  callers.

The service still trims optional target key versions before dry-run and
plan requests, still forwards `limit` query params, and still forwards
optional `batchSize` payloads for run endpoints.

## Test Coverage

Updated test file:

- `ecm-frontend/src/services/propertyEncryptionService.test.ts`

Covered cases:

- Status and encrypted definition endpoint forwarding.
- Rewrap/backfill dry-run and plan payload trimming.
- Rewrap/backfill job list, run, and cancel endpoint forwarding.
- Rejections for HTML fallback, malformed status counts, malformed
  definition booleans, malformed rewrap key-version counts, malformed
  backfill definition counts, invalid job status, and malformed backfill
  job snapshots.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/propertyEncryptionService.test.ts --watchAll=false
```

Result:

```text
PASS src/services/propertyEncryptionService.test.ts
Test Suites: 1 passed, 1 total
Tests:       10 passed, 10 total
```

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Diff hygiene:

```bash
git diff --check
```

Result: PASS.

## Commit

- `0e83916 fix(property-encryption): guard service responses`
- `a6621e5 fix(services): satisfy CI response guard casts`

## Follow-Up

The parallel `previewDiagnosticsService` core guard slice is being
developed in the Claude worktree
`.claude/worktrees/claude-preview-diagnostics-core-service-guards`.
It should be integrated only after it has tests and documentation; a
partial service-only patch is not enough for main.
