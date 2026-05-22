# SearchController Response-Contract Tests

Date: 2026-05-22

## Context

This is the second implementation slice from
`docs/BACKEND_RESPONSE_CONTRACT_TEST_TODO_20260521.md`, after the
FolderController response-contract slice.

The frontend guard closeout previously exposed a real backend wire-shape drift
where search result `path` could be JSON `null`. The goal of this slice is to
lock the Spring-side `/api/v1/search/query` contract so frontend guards are no
longer the first authoritative detector for this shape.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerResponseContractTest.java`

Updated:

- `docs/BACKEND_RESPONSE_CONTRACT_TEST_TODO_20260521.md`

Covered endpoint:

- `POST /api/v1/search/query`

Out of scope:

- Other `SearchController` endpoints.
- Search service implementation.
- Frontend predicate changes.
- Blob/download/CSV preview-search export methods.

## Design

The test uses standalone `MockMvc` with mocked controller dependencies and a
Jackson `ObjectMapper` configured with `JavaTimeModule` plus
`WRITE_DATES_AS_TIMESTAMPS` disabled. This matches Spring Boot's ISO
`LocalDateTime` response shape.

The test sends `/api/v1/search/query` with:

- `includeRequest: true`
- `include: ["results", "context", "stats", "pivot", "facets", "suggestions"]`

It locks the envelope top-level field order and verifies all optional envelope
sections are present when requested:

- `request`
- `results`
- `facets`
- `suggestions`
- `context`
- `stats`
- `pivot`
- `generatedAt`

For `SearchResult`, the test locks the current full wire contract:

- 31 declared fields from `SearchResult`
- plus the derived Jackson getter property `fileSizeFormatted`

The derived property is intentionally included. A direct Jackson check against
the compiled `SearchResult.class` showed `getFileSizeFormatted()` is serialized
as `fileSizeFormatted`, so the real wire shape has 32 properties even though the
class declares 31 fields.

The test also locks the previously trace-corrected nullable shape:

- `path` is present and JSON `null`
- `fileSize` is present and JSON `null`
- `lastModifiedDate` is present and JSON `null`

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Jackson wire-shape check for `SearchResult`:

```bash
jshell --class-path "ecm-core/target/classes:...jackson jars..."
```

Result: `SearchResult` serializes 32 properties:

```text
id, name, description, path, nodeType, parentId, mimeType, fileSize,
currentVersionLabel, createdBy, createdDate, lastModifiedBy, lastModifiedDate,
score, highlights, matchFields, highlightSummary, tags, categories,
correspondent, record, declaredBy, declaredAt, declaredVersionLabel,
declarationComment, recordCategoryId, recordCategoryName, recordCategoryPath,
previewStatus, previewFailureReason, previewFailureCategory,
fileSizeFormatted
```

Targeted Maven test attempted through the repository wrapper:

```bash
cd ecm-core
./mvnw -Dtest=SearchControllerResponseContractTest test
```

Result: blocked by the local environment before Maven startup:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

Global `mvn` is not installed on this machine. CI remains the authoritative
execution gate for this slice.

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
