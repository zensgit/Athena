# PreviewDiagnosticsController Response-Contract Tests

Date: 2026-05-22

## Context

This slice continues the backend response-contract track after the
SecurityController permissions follow-up. `PreviewDiagnosticsController` has a
large admin-only diagnostics surface, so this slice intentionally locks a small
high-traffic JSON subset instead of attempting all endpoints in one pass.

The frontend consumer is `PreviewDiagnosticsPage`, through
`previewDiagnosticsService`.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerResponseContractTest.java`

Covered JSON endpoints:

- `GET /api/v1/preview/diagnostics/failures`
- `GET /api/v1/preview/diagnostics/failures/summary`
- `GET /api/v1/preview/diagnostics/queue/summary`
- `POST /api/v1/preview/diagnostics/queue/cancel-active`
- `GET /api/v1/preview/diagnostics/queue/declined`
- `POST /api/v1/preview/diagnostics/queue/declined/export-async`

Out of scope:

- CSV and download endpoints.
- The full declined/requeue/rendition async-task families.
- Failure ledger, prevention, dead-letter, traces, and policy endpoints.
- Controller implementation changes.
- Frontend changes.

## Design

The test is a `@WebMvcTest` with admin authentication and mocked controller
dependencies. It complements the existing security/behavior test by asserting
the exact JSON field set for DTOs that already have behavioral coverage.

The slice locks these wire DTOs:

- `PreviewFailureSampleDto`
- `PreviewFailureSummaryDto`
- `PreviewFailureStatusCountDto`
- `PreviewFailureCategoryCountDto`
- `PreviewFailureReasonCountDto`
- `PreviewQueueDiagnosticsSummaryDto`
- `PreviewQueueDiagnosticsItemDto`
- `PreviewQueueCancelActiveResponseDto`
- `PreviewQueueCancelActiveItemDto`
- `PreviewQueueDeclinedSummaryDto`
- `PreviewQueueDeclinedCategoryCountDto`
- `PreviewQueueDeclinedItemDto`
- `PreviewQueueDeclinedExportAsyncCreateResponseDto`

The tests use deterministic UUIDs, timestamps, and snapshots to lock:

- explicit JSON nulls for nullable fields such as `previewFailureReason`,
  `previewLastUpdated`, `nextEligibleAt`, and `deduplicatedFromTaskId`;
- `Instant` serialization as ISO strings on queue timestamps;
- nested array item field sets for counts, queue items, declined items, and
  cancel-active results;
- the async create-response field set without covering CSV download behavior.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=PreviewDiagnosticsControllerResponseContractTest test
```

Result: blocked by the local environment before Maven startup:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

CI remains the authoritative execution gate for this slice.

## Expected CI Gate

After push, the required confirmation is the normal GitHub Actions matrix:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate

If CI is green, append a `CI Follow-Up` section with the run id and commit a
doc-only `[skip ci]` closeout.
