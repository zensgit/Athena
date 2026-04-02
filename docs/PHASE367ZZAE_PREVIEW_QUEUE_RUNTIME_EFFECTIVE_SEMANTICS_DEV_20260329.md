# Phase367ZZAE Preview Queue Runtime Effective Semantics

## Goal

Move preview queue runtime handling from message-driven heuristics to explicit `PreviewResult` semantics so retry, prevention, and dead-letter behavior all follow the same effective status/category contract.

## Problem

`PreviewQueueService` had already converged enqueue-time gating onto effective preview semantics, but runtime processing still relied too heavily on `result.getMessage()`:

- `shouldRetry(...)` could be misled by message text even when `PreviewResult.status` or `failureCategory` already said otherwise
- `maybeAutoBlock(...)` could reclassify an explicit permanent/unsupported outcome back into another category
- `markDeadLetter(...)` could drift from the actual `PreviewResult.failureCategory`
- retry scheduling and failure persistence used `message` instead of the richer `failureReason`

That left the queue execution path behind the rest of the rendition-backed convergence work.

## Design

### Introduce explicit PreviewResult runtime helpers

File:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

Add helper methods:

- `resolveResultStatus(...)`
- `resolveResultFailureReason(...)`
- `resolveResultFailureCategory(...)`
- `normalizeFailureCategory(...)`
- `firstNonBlank(...)`

These helpers make runtime processing prefer:

1. explicit `PreviewResult.status`
2. explicit `PreviewResult.failureCategory`
3. explicit `PreviewResult.failureReason`
4. only then message-based fallback classification

### Make retry decisions status/category aware

Update `shouldRetry(...)` so it now:

- immediately respects `retryNeeded=true`
- treats explicit `UNSUPPORTED` as terminal
- treats explicit `TEMPORARY` category as retryable
- treats explicit `UNSUPPORTED/PERMANENT` category as terminal
- only falls back to message-based classification when explicit status/category is absent

This removes the old problem where misleading message text could override a richer result contract.

### Reuse the same semantics for prevention and dead-letter

Update:

- `maybeAutoBlock(...)`
- `markDeadLetter(...)`

to reuse the same resolved reason/category instead of independently reclassifying raw messages.

As a result:

- unsupported outcomes stay unsupported
- permanent outcomes stay permanent
- retry-exhausted paths no longer drift from the original `PreviewResult`

### Use richer failure reason when updating queue-local document state

Update runtime queue handling in both memory and redis paths so:

- `markRetrying(...)`
- `markFailed(...)`

prefer `PreviewResult.failureReason` over plain `message`.

This keeps queue-local document state aligned with the same reason operators see in diagnostics and rendition-backed surfaces.

## Result

Preview queue runtime is no longer a message-only island. Retry, auto-block, dead-letter, and queue-local failure persistence now all prefer the same explicit `PreviewResult` status/category/reason contract, which is the next required step toward making rendition-backed semantics the real execution-path source of truth.
