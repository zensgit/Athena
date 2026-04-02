# Phase167 Dev: Preview Dead-Letter Redis Persistence Hardening

## Date
2026-03-06

## Goal
Strengthen dead-letter diagnostics so entries survive service restarts and remain operator-visible in clustered deployments.

## Backend changes
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewDeadLetterRegistry.java`
  - add Redis-first backend with in-memory fallback.
  - add redis index + entry keys:
    - `ecm:preview:deadletter:index`
    - `ecm:preview:deadletter:entry:<documentId>`
  - add bounded retention and TTL controls:
    - `record(...)` upserts and increments `occurrences` in Redis.
    - `list(limit)` reads latest entries by sorted index.
    - `remove(...)` clears both index and entry key.
  - add config/runtime metadata:
    - `isRedisEnabled()`
    - `isRedisActive()`
    - `getTtlMs()`
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - extend dead-letter diagnostics payload with:
    - `redisEnabled`
    - `backendMode` (`REDIS` / `MEMORY`)
    - `ttlMs`
- `ecm-core/src/main/resources/application.yml`
  - dead-letter config expansion:
    - `ecm.preview.dead-letter.redis.enabled`
    - `ecm.preview.dead-letter.ttl-ms`

## Frontend changes
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - extend `PreviewDeadLetterDiagnostics` type with:
    - `backendMode`
    - `redisEnabled`
    - `ttlMs`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - dead-letter panel adds chips:
    - backend mode
    - TTL (human-readable seconds/minutes/hours)
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - dead-letter mock payload includes new fields.
  - add assertions for `Backend REDIS` and TTL chip.

## Test additions
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewDeadLetterRegistryRedisBackendTest.java`
  - verifies Redis-backed record/list/remove behavior.
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - asserts dead-letter diagnostics includes `redisEnabled/backendMode/ttlMs`.
- `scripts/phase164-preview-day7-delivery-gate.sh`
  - backend targeted test list now includes `PreviewDeadLetterRegistryRedisBackendTest`.
