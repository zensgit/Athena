# Phase157 Dev: Explicit Preview Retry-Hint Pipeline

## Date
2026-03-06

## Goal
Adopt explicit retry signaling for preview generation so queue retry decisions do not rely only on error text heuristics.

## Borrowed design cues from Alfresco
- `CombinedConfig` uses explicit retry header (`X-Alfresco-Retry-Needed`) to control retry decisions.
- Athena now applies the same principle to CAD preview rendering responses.

## Changes
- File: `ecm-core/src/main/java/com/ecm/core/preview/PreviewResult.java`
  - Added fields:
    - `retryNeeded`
    - `retryHint`

- File: `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
  - `shouldRetry(...)` now prioritizes `result.retryNeeded == true`.

- File: `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
  - CAD render HTTP call switched to `exchange(...)` and parses retry headers:
    - `X-Ecm-Retry-Needed`
    - `X-Alfresco-Retry-Needed`
  - If retry is requested by header:
    - preview result is marked as `supported=false` with `retryNeeded=true`
    - reason/hint message is persisted for operator diagnostics
    - metric reason `retry_needed` is emitted
  - CAD thumbnail path handles retry-needed/no-payload safely by returning default thumbnail.

## Outcome
- Queue retry behavior is now protocol-driven when available.
- Temporary backend render instability can trigger retries even when message text is ambiguous.
