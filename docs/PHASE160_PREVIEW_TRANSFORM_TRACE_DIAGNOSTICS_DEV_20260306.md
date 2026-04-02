# Phase160 Dev: Preview Transform Trace Diagnostics

## Date
2026-03-06

## Goal
Introduce request-id keyed preview transform traces so operators can quickly see start/fail/retry/success lifecycle without scanning backend logs.

## Borrowed pattern from Alfresco
- `TransformerDebugLog`:
  - request-id grouped debug lines
  - bounded in-memory retention
- `LocalFailoverTransform`:
  - explicit attempt/failover step visibility

## Backend changes
- Added request-id trace buffer:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewTransformTraceBuffer.java`
  - bounded in-memory traces (max requests + max events per request)
  - request-id lifecycle:
    - `start(...)`
    - `append(...)`
    - `complete(...)`
    - `snapshot(...)`
- Added config bridge:
  - `ecm-core/src/main/resources/application.yml`
  - `ecm.preview.trace.enabled`
  - `ecm.preview.trace.max-requests`
  - `ecm.preview.trace.max-events-per-request`
- Integrated trace lifecycle into preview pipeline:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
  - records:
    - route selection (`cad/pdf/image/office/text`)
    - CAD endpoint attempts/success/failure/retry-hint
    - final outcome
- Added trace id propagation in result object:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewResult.java`
  - new field: `traceRequestId`
- Integrated queue lifecycle trace events:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
  - records:
    - queue attempt
    - retry scheduled
    - retry exhausted
    - queue done
- Added admin diagnostics API:
  - `GET /api/v1/preview/diagnostics/traces?limit=20&requestId=pv-1`
  - file: `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

## Frontend changes
- Added trace API types + client call:
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - `getTransformTraces(limit, requestId?)`
- Added `Transform Trace Diagnostics` panel:
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - supports:
    - trace limit selector
    - request-id filter
    - trace table (status/events/latest message/timestamps)
- Updated mocked E2E:
  - `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - new route mock for `/preview/diagnostics/traces`
  - panel visibility assertions

## Tests updated
- New unit test:
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewTransformTraceBufferTest.java`
- Updated controller security test:
  - `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - includes admin + forbidden checks for `/preview/diagnostics/traces`
- Updated queue tests for new dependency wiring:
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceRedisBackendTest.java`
