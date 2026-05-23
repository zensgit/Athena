# RecordsManagementController Response-Contract Tests

Date: 2026-05-22

## Context

This slice continues the backend response-contract track after the
SecurityController and PreviewDiagnosticsController slices. It targets the
primary `RecordsManagementController` JSON surface consumed by
`RecordsManagementPage` through `recordsManagementService`.

`RmReportPresetController` is a separate controller with its own existing test
coverage, so this slice does not mix report preset scheduling/delivery contracts
into the RM core read-contract slice.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerResponseContractTest.java`

Covered JSON endpoints:

- `GET /api/v1/records`
- `GET /api/v1/records/summary`
- `GET /api/v1/records/audit`
- `GET /api/v1/records/operations`
- `GET /api/v1/records/activity-timeline`
- `GET /api/v1/records/activity-highlights`
- `GET /api/v1/records/file-plans`
- `GET /api/v1/records/categories`

Out of scope:

- CSV report/export endpoints.
- `RmReportPresetController` endpoints.
- RM mutation endpoints.
- Node record declaration mutation endpoints.
- Controller implementation changes.
- Frontend changes.

## Design

The test uses standalone `MockMvc` with mocked
`RecordsManagementService`/`RmReportPresetService`, a `Pageable` argument
resolver for `/records/audit`, and a Jackson `ObjectMapper` configured with
`JavaTimeModule` plus `WRITE_DATES_AS_TIMESTAMPS` disabled.

The slice locks these wire DTOs:

- `RecordDeclarationDto`
- `RecordsSummaryDto`
- `SummaryBucketDto`
- Spring `Page` envelope for `RecordAuditEntryDto`
- `RecordAuditEntryDto`
- `RecordsOperationsTelemetryDto`
- `GovernedImportJobDto`
- `GovernedTransferJobDto`
- `RecordsActivityTimelineDto`
- `RecordsActivityPointDto`
- `RecordsActivityHighlightsDto`
- `RecordsActivityWindowDto`
- `RecordsActivityPeakDto`
- `FilePlanDto`
- `RecordCategoryDto`

The tests lock:

- explicit JSON nulls for nullable fields such as record category fields,
  audit details, file-plan descriptions, operation messages, and job
  completion timestamps;
- `LocalDateTime` serialization as ISO strings;
- the page envelope used by `/records/audit`, including the observed Spring
  `PageImpl` field order fixed by `b03fe9b`;
- the nested summary-bucket, activity-window, governed-import-job, and
  governed-transfer-job field sets.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=RecordsManagementControllerResponseContractTest test
```

Result: blocked by the local environment before Maven startup:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

CI remains the authoritative execution gate for this slice.

## CI Follow-Up

Final CI:

- GitHub Actions run: `26326650362`
- Head: `b03fe9b6b309dfd6cc5419b3dd595e8af112c20f`
- Result: `success`

All seven jobs passed:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate
