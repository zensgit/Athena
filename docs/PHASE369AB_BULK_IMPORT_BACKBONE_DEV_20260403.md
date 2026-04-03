# Phase369AB Bulk Import Backbone Dev

Date: 2026-04-03

## Goal

Add a first usable bulk import backbone for Athena so admins can import a browser-selected folder tree into the repository with tracked job status, recursive folder creation, and explicit conflict handling.

## Scope Delivered

### Backend

- Added `ImportJob` persistence model with tracked status, conflict policy, counters, current item, message, and error log.
- Added Liquibase migration `051-create-import-jobs-table.xml`.
- Added `ImportJobRepository`.
- Added `BulkImportService` with:
  - staged file handling
  - async job execution
  - recursive folder chain creation from relative paths
  - `SKIP / RENAME / OVERWRITE` conflict policy enforcement
  - job list/get/cancel contract
- Added `BulkImportController` under both `/api/bulk-import` and `/api/v1/bulk-import`.

### Frontend

- Added `bulkImportService.ts` for start/get/list/cancel job APIs.
- Added `api.postFormData(...)` helper for multi-file multipart requests.
- Added `BulkImportPage.tsx`:
  - select folder
  - select files
  - target folder id
  - conflict policy
  - recent jobs table with progress and cancel action
  - auto-refresh for running jobs
- Added admin route `/admin/bulk-import`.
- Added `Bulk Import` admin menu entry in `MainLayout`.

### Focused Utilities

- Added `bulkImportUtils.ts` for stable relative-path extraction and selection summary.
- Added `bulkImportUtils.test.ts`.

## Design Notes

- This phase intentionally uses browser-supplied relative paths instead of trying to scan arbitrary local server paths.
- The backbone is job-oriented even though file staging is initiated in a single request. This keeps the contract extendable for later import progress, history, or queued execution improvements.
- Conflict handling is applied at import time, not hidden inside upload fallback logic, so operators can choose the behavior per import run.

## Deferred

- Dedicated import history filters/search
- Rich job detail page
- ZIP/archive import
- Import notifications/webhooks
- Server-side filesystem scan
