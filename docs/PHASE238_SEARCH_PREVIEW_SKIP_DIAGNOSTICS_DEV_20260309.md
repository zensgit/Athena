# Phase 238 - Search Preview Skip Diagnostics (Dev)

Date: 2026-03-09  
Owner: Codex (parallel delivery stream)

## Objective

Enhance search-scope preview dry-run/queue operations with explicit skip diagnostics so operators can understand why candidates were not queued.

This closes an observability gap for large-scale retry governance and aligns with benchmark direction toward more explainable async recovery operations.

## Backend design

File:
- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`

### Added skip breakdown model

New DTO:

- `PreviewQueueBySearchSkipCountDto(reason, count)`

Added to responses:

1. `PreviewQueueBySearchResponse.skipBreakdown`
2. `PreviewQueueBySearchDryRunResponse.skipBreakdown`

### Skip reason capture in scan pipeline

In `collectMatchedRetryableFailures(...)`, scan path now accumulates skip counters:

1. `NON_RETRYABLE`
2. `REASON_MISMATCH`
3. `MISSING_DOCUMENT_ID`
4. `DUPLICATE_DOCUMENT_ID`

Counters are sorted (count desc, reason asc) and returned as `skipBreakdown`.

### CSV export enhancement

Dry-run CSV export now includes dedicated skip section:

1. `skipReason,count`
2. all aggregated skip diagnostics rows

This applies to sync and async export paths (shared CSV builder).

## Frontend design

Files:
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

### Service contract

Added type:

- `PreviewQueueSearchSkipCount`

Added optional fields:

1. `PreviewQueueSearchBatchResult.skipBreakdown`
2. `PreviewQueueSearchDryRunResult.skipBreakdown`

### UI diagnostics panel

Advanced Search preview batch block now renders:

1. `Skipped diagnostics`
2. Top skip reason chips with counts

This is shown for both dry-run and queue batch responses when backend returns skip breakdown.

## Benchmark mapping

This phase strengthens “operator explainability” and “actionability” in batch recovery workflows, which is required for surpassing baseline enterprise ECM retry governance patterns.

