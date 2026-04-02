# Phase164 Dev: Preview Circuit Breaker and Prevention Persistence Hardening

## Date
2026-03-06

## Goal
Harden preview diagnostics and recovery behavior for production operations:
- expose CAD circuit-breaker state/config in admin diagnostics.
- persist rendition-prevention registry in Redis with TTL fallback safety.
- align frontend diagnostics panel and mocked regression payload with the new backend model.

## Backend changes
- `ecm-core/src/main/java/com/ecm/core/preview/CadRenderFailoverTracker.java`
  - added `getHalfOpenTrialTimeoutMs()` for diagnostics config projection.
  - circuit-breaker state model retained in snapshot:
    - `consecutiveFailureCount`
    - `circuitState`
    - `circuitOpenUntil`
    - `lastCircuitOpenedAt`
    - `halfOpenInFlight`
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - extended `GET /api/v1/preview/diagnostics/cad-failover` payload:
    - top-level breaker config and enablement.
    - per-endpoint circuit state fields.
- `ecm-core/src/main/resources/application.yml`
  - added CAD breaker configuration keys:
    - `ecm.preview.cad.circuit-breaker.enabled`
    - `ecm.preview.cad.circuit-breaker.failure-threshold`
    - `ecm.preview.cad.circuit-breaker.open-ms`
    - `ecm.preview.cad.circuit-breaker.half-open-timeout-ms`
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewRenditionPreventionRegistry.java`
  - Redis-backed blocked-entry index + TTL entry persistence (with memory fallback) already wired in this phase batch.

## Frontend changes
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - extended CAD diagnostics types for breaker config/state fields.
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - added CAD breaker summary chips:
    - breaker on/off
    - threshold/open-window info
  - added endpoint columns:
    - `Circuit`
    - `Consecutive Failure`
    - `Open Until`
  - state chip coloring:
    - `OPEN` -> error
    - `HALF_OPEN` -> warning
    - `CLOSED` -> success
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - updated mocked `cad-failover` payload to include new fields.
  - added UI assertions for breaker summary chips.

## Test updates
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - updated `EndpointStats` constructor usage to new record signature.
  - added assertions for circuit-breaker config/state fields in API response.
