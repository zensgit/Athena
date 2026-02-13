# Phase 3 Day 5 Design: Redis Queue Backend (Preview + OCR)

Date: 2026-02-13

## Summary

Day 5 adds an opt-in Redis-backed queue backend for preview and OCR scheduling while preserving the existing in-memory behavior as default.  
Goal: keep queued jobs across core restarts and prevent duplicate processing.

## Scope

- `ecm-core` queue persistence for:
  - preview generation jobs
  - OCR extraction jobs
- Feature-flagged backend selection:
  - `ecm.preview.queue.backend=memory|redis`
  - `ecm.ocr.queue.backend=memory|redis`
- No public REST API contract break.

## Key Changes

- Added Redis dependency in `ecm-core/pom.xml`:
  - `spring-boot-starter-data-redis`
- Added Redis queue storage helper:
  - `ecm-core/src/main/java/com/ecm/core/queue/RedisScheduledQueueStore.java`
- Integrated Redis scheduling path into:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
  - `ecm-core/src/main/java/com/ecm/core/ocr/OcrQueueService.java`
- Added env plumbing:
  - `.env`
  - `docker-compose.yml`

## Redis Data Model

- Preview:
  - schedule ZSET: `ecm:queue:preview:schedule`
  - attempts HASH: `ecm:queue:preview:attempts`
  - lock key prefix: `ecm:queue:preview:lock:`
- OCR:
  - schedule ZSET: `ecm:queue:ocr:schedule`
  - attempts HASH: `ecm:queue:ocr:attempts`
  - force HASH: `ecm:queue:ocr:force`
  - lock key prefix: `ecm:queue:ocr:lock:`

## Processing Semantics

- Job identity is document id (`docId`) for deduplication.
- Poller claims due docs from ZSET by `nextAttemptAt` score.
- Per-doc Redis lock (TTL) protects against duplicate workers and crash-loss.
- Success/final-failure clears attempts/force entries.
- Retry path increments attempts and reschedules with backoff.

## Compatibility

- Default backend is still `memory`.
- Existing queue APIs stay unchanged.
- Redis backend is activated only when flags are set to `redis`.

## Risk Notes

- Testcontainers Redis tests can be skipped if local Docker/Testcontainers handshake is unavailable.
- Lock TTL must remain greater than normal single-job processing time to avoid duplicate claims.
