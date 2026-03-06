# Phase 152: Preview Diagnostics Window Filter (Development)

## Date
2026-03-06

## Goal
- Add time-window filtering (`days`) to preview diagnostics list and summary so operators can switch between short-term and broader failure windows.

## Scope
- Backend:
  - `GET /api/v1/preview/diagnostics/failures?limit=<n>&days=<d>`
  - `GET /api/v1/preview/diagnostics/failures/summary?sampleLimit=<n>&days=<d>`
- Frontend:
  - Add `Days` selector (Last 7 days / Last 30 days / All time)
  - Pass selected `days` to list + summary requests

## Design
1. Backend repository adds window-aware queries with nullable `updatedSince`.
2. Controller normalizes `days`:
   - `0` -> all time (no date filter)
   - `<0` -> fallback to default 7
   - positive -> clamp to `1..365`
3. Summary DTO adds:
   - `windowDays`
   - `windowStart` (nullable when all-time)
4. Frontend binds `days` selector to reload logic and keeps list/summary in sync.

## Changed Files
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/main/java/com/ecm/core/repository/DocumentRepository.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
