# Phase161 Dev: Preview Failure Policy Profiles

## Date
2026-03-06

## Goal
Provide profile-based retry policy controls (retry count, base delay, backoff multiplier, quiet period) so preview retry behavior is tunable by mime/type class and visible to operators.

## Borrowed pattern from Alfresco
- `FailureHandlingOptions`:
  - retry count + retry period + quiet period abstraction.
- `FailedThumbnailInfo` usage pattern:
  - preserve failure metadata and drive reprocessing decisions.

## Backend changes
- Added policy registry:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewFailurePolicyRegistry.java`
  - built-in profiles: `default`, `cad`, `pdf`, `office`, `image`, `text`
  - supports runtime update with strict bounds:
    - attempts: `1..10`
    - retry delay: `1000..3600000 ms`
    - backoff multiplier: `1.0..10.0`
    - quiet period: `0..86400000 ms`
- Queue integration:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
  - resolves profile by mime/name
  - applies profile max attempts and backoff delay
  - applies quiet-period enqueue guard for FAILED docs when not forced
- Admin APIs:
  - `GET /api/v1/preview/diagnostics/policies`
  - `PUT /api/v1/preview/diagnostics/policies/{profileKey}`
  - file: `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

## Frontend changes
- API client:
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - added:
    - `getFailurePolicies()`
    - `updateFailurePolicy(...)`
- Diagnostics UI:
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - new `Failure Policy Profiles` panel:
    - inline editing for attempts/delay/backoff/quiet period
    - impact preview (retry delay schedule)
    - save action per profile
- Mocked E2E:
  - `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - added policy list/update mocks and panel assertions.

## Tests updated
- Added:
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewFailurePolicyRegistryTest.java`
- Updated:
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceRedisBackendTest.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
