# Phase 16 - Preview Status Persistence (Dev) - 2026-02-03

## Goal
Ensure preview job status and failure reason are persisted even when the preview queue encounters errors, aligning with Phase 1 P0 requirement (ready/failed/processing + failure reason).

## Scope
- Preview queue marks a document as PROCESSING when retrying a transient failure.
- Preview queue marks a document as FAILED with a failure reason when retries are exhausted or an exception occurs before PreviewService can persist an outcome.
- Add unit coverage for the exception path.

## Implementation Notes
- Added `markRetrying` and `markFailed` helpers to `PreviewQueueService`.
- On retry scheduling, we persist PROCESSING + failure reason so the UI can surface the last failure while retrying.
- When retries are exhausted (or exception path), we persist FAILED + failure reason.

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`

## API/Schema Notes
- No schema changes required; uses existing `preview_status`, `preview_failure_reason`, `preview_last_updated` columns.
