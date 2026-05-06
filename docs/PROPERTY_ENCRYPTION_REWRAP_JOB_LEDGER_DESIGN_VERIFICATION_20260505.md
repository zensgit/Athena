# Property Encryption Rewrap Job Ledger Design Verification

Date: 2026-05-05

## Context

The property encryption admin page already exposes rewrap dry-run diagnostics, but full rewrap execution was still a remaining benchmark gap.

This slice adds the API-first ledger foundation for rewrap operations. It intentionally stops before mutating node payloads: admins can persist an executable dry-run snapshot as a planned job and inspect recent planned jobs, but there is no run/cancel endpoint yet.

## Design

### Ledger Entity

Added `PropertyEncryptionRewrapJob` backed by `property_encryption_rewrap_jobs`.

The ledger captures:

- request metadata: `id`, `status`, `targetKeyVersion`, `requestedBy`, `requestedAt`, `startedAt`, `finishedAt`
- dry-run impact snapshot: candidate node count, total encrypted value count, values already on target key, values requiring rewrap, malformed/unversioned count
- future execution counters: processed, rewrapped, skipped, failed
- diagnostic JSONB arrays: key-version counts, missing source key versions, warnings
- terminal metadata: `lastError`, `createdAt`, `updatedAt`, optimistic `version`

Migration:

```text
091-create-property-encryption-rewrap-jobs.xml
```

The master changelog includes it after the backfill-job ledger migration and before initial data.

### Service API

Added rewrap ledger methods to `PropertyEncryptionOperationsService`:

- `planRewrapJob(request, requestedBy)`
- `listRewrapJobs(limit)`
- `getRewrapJob(jobId)`

Planning reuses the existing `dryRunRewrap` path. If the dry-run is not executable, the service rejects the request and does not persist a job. This keeps the ledger from recording known-bad execution promises.

### Admin Controller API

Added admin-only endpoints under `/api/v1/admin/property-encryption`:

- `POST /rewrap-jobs/plan`
- `GET /rewrap-jobs?limit=...`
- `GET /rewrap-jobs/{jobId}`

All endpoints inherit the existing `hasRole('ADMIN')` controller guard.

## Verification

### Backend Targeted Tests

Command:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest \
  test
```

Result:

```text
Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Coverage added:

- executable rewrap dry-run snapshot persists as `PLANNED`
- non-executable rewrap dry-run rejects without save
- list/get maps persisted JSONB snapshots
- non-admin users receive 403 for new endpoints
- admins can plan/list/get rewrap jobs

### PostgreSQL Integration Compile/Skip Check

Command:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=PropertyEncryptionBackfillPostgresIntegrationTest \
  test
```

Result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Environment note: Docker is not available on this host, so the Testcontainers PostgreSQL test skipped with the expected Docker-environment assumption.

### Wrapper/Docker Note

The repo `./mvnw` delegates to Docker. On this machine it failed before Maven startup:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock
```

To keep validation moving, a temporary Maven 3.9.9 binary under `/tmp` was used with the repo-local `.m2-cache/repository`. No Maven binary or tool config was added to the repository.

### XML And Diff Checks

Commands:

```bash
xmllint --noout \
  ecm-core/src/main/resources/db/changelog/changes/091-create-property-encryption-rewrap-jobs.xml \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml

git diff --check
```

Result: both passed.

## Remaining Development Estimate

### Property Encryption Benchmark

Remaining work after this slice: about `4.5-7.5 person-days`.

Main remaining slices:

- Rewrap execution backend: `2.5-4 person-days`
  - run/cancel endpoints
  - JSONB candidate selection
  - decrypt old payload, protect with target key, CAS update
  - terminal counters, recovery and PostgreSQL integration coverage

- Rewrap execution UI: `1-2 person-days`
  - show planned rewrap ledger
  - expose run/cancel only after backend execution exists
  - add unit and mocked browser coverage

- Runtime masking/redaction closeout: `1-2 person-days`
  - confirm generic property editors do not expose protected payloads incorrectly
  - document readable/indexable behavior
  - add acceptance checks if product policy requires masking assertions

- CI closeout: `0.5 person-day`
  - run the Docker-backed property encryption gate on a Docker-capable runner
  - update final acceptance matrix

## Next Recommendation

Do the backend rewrap execution engine next, still API-first. The ledger now gives the execution engine a durable state model, but exposing UI run/cancel before the backend can safely mutate payloads would recreate the same cross-boundary gap this project has been closing.
