# DocumentController Version and Checkout Response-Contract Tests

Date: 2026-05-23

## Context

This slice continues the backend response-contract track after the
RecordsManagementController follow-up. The TODO identifies two high-consumption
DocumentController JSON endpoints: version history and checkout info.

The frontend consumer is `nodeService`, mainly the version-history and
lock/checkout subdomains.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerVersionCheckoutResponseContractTest.java`

Covered JSON endpoints:

- `GET /api/v1/documents/{documentId}/versions`
- `GET /api/v1/documents/{documentId}/checkout-info`

Out of scope:

- Upload/download endpoints.
- Version download/revert/compare endpoints.
- Paged version history.
- Checkout mutation endpoints.
- Checkout lineage.
- Preview/OCR/conversion/annotation endpoints.
- Controller implementation changes.
- Frontend changes.

## Design

The test uses standalone `MockMvc` with mocked DocumentController dependencies
and a Jackson `ObjectMapper` configured with `JavaTimeModule` plus
`WRITE_DATES_AS_TIMESTAMPS` disabled.

The slice locks these wire DTOs:

- `VersionDto`
- `CheckoutInfoDto`

The tests lock:

- explicit JSON nulls for nullable `VersionDto` fields such as `comment`,
  `createdDate`, `creator`, `contentHash`, `contentId`, and `status`;
- `LocalDateTime` serialization as ISO strings for non-null version dates;
- checkout baseline/current booleans derived from the document state;
- explicit JSON null for `checkoutDate` on a checkout held by another user;
- all checkout action affordance booleans and `blockingReason`.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=DocumentControllerVersionCheckoutResponseContractTest test
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
