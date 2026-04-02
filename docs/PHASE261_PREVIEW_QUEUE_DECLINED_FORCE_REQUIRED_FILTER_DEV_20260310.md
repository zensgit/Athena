# Phase 261 - Preview Queue Declined ForceRequired Filter & Category Breakdown (Dev)

Date: 2026-03-10  
Stream: Day2-7 Stream B (queue governance)

## Objective

Extend declined queue governance with:

1. `forceRequired` dimension filtering (`ANY|YES|NO`);
2. filter propagation across summary/export/requeue/dry-run/clear;
3. category-level breakdown with force-required counts for faster operator decisions.

## Backend changes

### 1) Declined endpoints add `forceRequired` filter

File:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

Updated endpoints:
- `GET /api/v1/preview/diagnostics/queue/declined`
- `GET /api/v1/preview/diagnostics/queue/declined/export`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run`
- `POST /api/v1/preview/diagnostics/queue/declined/clear`

Added:
- filter normalization `normalizeQueueDeclinedForceRequiredFilter(...)`
- force-required-aware mapping in declined list filtering

### 2) Declined summary payload enriched

Added DTO fields:
- `forceRequiredFilter`
- `forceRequiredCount`
- `categoryCounts` (new `PreviewQueueDeclinedCategoryCountDto`)

Added helper:
- `mapQueueDeclinedCategoryCounts(...)` returning per-category:
  - `category`
  - `count`
  - `forceRequiredCount`

### 3) CSV and audit alignment

Updated declined CSV:
- new header column `forceRequiredFilter`
- each row now includes current filter context for force-required dimension

Updated audit text for:
- export
- requeue
- requeue dry-run
- clear

All now include `forceRequired=<value>`.

## Frontend changes

### 1) Service contracts + compatibility

File:
- `ecm-frontend/src/services/previewDiagnosticsService.ts`

Added:
- `PreviewQueueDeclinedForceRequiredFilter` type
- `PreviewQueueDeclinedCategoryCount` type
- `forceRequiredFilter/forceRequiredCount/categoryCounts` in declined summary/result types

Updated queue declined methods to pass `forceRequired` query param:
- `getQueueDeclinedSummary`
- `exportQueueDeclinedCsv`
- `requeueQueueDeclined`
- `dryRunQueueDeclinedRequeue`
- `clearQueueDeclined`

Compatibility:
- preserved old call forms via overloads and argument resolution.

### 2) Preview diagnostics UI

File:
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

In `Preview Queue Declined` panel:
- added `forceRequired` filter dropdown (`ANY/YES/NO`)
- wired filter into refresh/export/requeue/dry-run/clear calls
- clear button now resets category + forceRequired + query
- added chips:
  - total force-required count
  - active force-required filter
  - category breakdown with `count` and `forceRequiredCount`
- dry-run summary alert now shows `forceRequiredFilter`.

### 3) Mocked e2e alignment

File:
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

Updated mocked declined routes to:
- parse and apply `forceRequired`
- emit new fields in summary/action payloads
- emit CSV with `forceRequiredFilter` column

UI scenario now selects `Force required: no` before declined actions, and assertions verify query-param propagation.

## Test updates

File:
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

Added/updated assertions for:
- declined summary `forceRequiredFilter/forceRequiredCount/categoryCounts`
- `forceRequired=NO` filter behavior
- export/requeue/dry-run/clear URL filter propagation and response echo.
