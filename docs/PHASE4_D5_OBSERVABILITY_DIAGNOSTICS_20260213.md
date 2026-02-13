# Phase 4 - Day 5: Preview Observability + Diagnostics (2026-02-13)

## Goal

Make preview generation behavior observable and easier to debug:

1. Metrics to understand volumes of `READY`/`FAILED`/`UNSUPPORTED` by MIME type and failure category.
2. Logs that record a structured outcome for failures without flooding the logs.
3. Admin-only diagnostics endpoint to sample recent failures with derived categories.

## Changes

### Backend: Metrics + Logs

Updated:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`

Behavior:

- Records a counter `preview_generation_total` with tags:
  - `status`: `READY|FAILED|UNSUPPORTED|...`
  - `category`: `TEMPORARY|PERMANENT|UNSUPPORTED|NONE`
  - `mimeType`: normalized to lowercase and stripped of `; charset=...`
- Writes one structured log line for non-`READY` outcomes:
  - `UNSUPPORTED` at `INFO`
  - `FAILED` at `WARN`

Example log:

```
Preview generation outcome: documentId=<uuid> status=UNSUPPORTED category=UNSUPPORTED mimeType=application/octet-stream reason=...
```

### Backend: Recent Failures API

Added:

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - `GET /api/v1/preview/diagnostics/failures?limit=50`
  - Admin-only: `@PreAuthorize("hasRole('ADMIN')")`

Response is a list of:

- `id`, `name`, `path`
- `mimeType`
- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory` (derived using `PreviewFailureClassifier`)
- `previewLastUpdated`

Repository support:

- `ecm-core/src/main/java/com/ecm/core/repository/DocumentRepository.java`
  - `findRecentPreviewFailures(List<PreviewStatus> statuses, Pageable pageable)`

Tests:

- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - Verifies admin-only access and limit clamping.

## Verification

### Backend Unit/Integration Tests

```bash
cd ecm-core
mvn -q test
```

Result: PASS (2026-02-13)

### Runtime Smoke (API + Metrics + Logs)

1) Get an admin token:

```bash
./scripts/get-token.sh admin admin
```

2) List recent failures:

```bash
curl -fsS -H "Authorization: Bearer $(cat tmp/admin.access_token)" \
  "http://localhost:7700/api/v1/preview/diagnostics/failures?limit=5" | jq .
```

3) Trigger a preview to emit a metric + log (use an UNSUPPORTED file):

```bash
DOC_ID="<some-document-id>"
curl -fsS -H "Authorization: Bearer $(cat tmp/admin.access_token)" \
  "http://localhost:7700/api/v1/documents/${DOC_ID}/preview" | jq .
```

4) Confirm the metric exists:

```bash
curl -fsS -H "Authorization: Bearer $(cat tmp/admin.access_token)" \
  http://localhost:7700/actuator/prometheus | rg '^preview_generation_total' | head
```

5) Confirm a structured log was emitted:

```bash
docker logs --tail 200 athena-ecm-core-1 | rg "Preview generation outcome" | tail
```

## Notes

- The counter is only created after the first preview outcome is recorded (Micrometer behavior).
- `UNSUPPORTED` is logged at `INFO` to avoid treating permanent unsupported formats as operational failures.

