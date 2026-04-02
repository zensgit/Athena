# Phase159 Dev: Preview CAD Failover Diagnostics Panel

## Date
2026-03-06

## Goal
Expose CAD render failover chain and endpoint-level recent outcomes to admin operators.

## Borrowed pattern from Alfresco
- `LocalFailoverTransform`: sequential fallback strategy.
- `TransformerDebugLog`: operator-oriented diagnostics visibility.

## Backend
- Added endpoint registry:
  - `ecm-core/src/main/java/com/ecm/core/preview/CadRenderEndpointRegistry.java`
  - resolves ordered endpoint chain from:
    - `ecm.preview.cad.render-url`
    - `ecm.preview.cad.render-fallback-urls`
- Added failover tracker:
  - `ecm-core/src/main/java/com/ecm/core/preview/CadRenderFailoverTracker.java`
  - records per-endpoint success/failure counts and last timestamps/reason.
- Preview service integration:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
  - records endpoint outcomes during CAD render attempts.
- Diagnostics API:
  - `GET /api/v1/preview/diagnostics/cad-failover`
  - file: `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

## Frontend
- Service API type + method:
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
- New panel on preview diagnostics page:
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - shows:
    - CAD enabled/configured chips
    - endpoint table (success/failure counts, last success/failure, last failure reason)
- Mocked E2E updates:
  - `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - added route mock and visibility assertions for new panel.

## Config update
- `ecm-core/src/main/resources/application.yml`
  - added `ecm.preview.cad.render-fallback-urls` env bridge.
