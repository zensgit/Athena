# Phase 170 - Ops Policy Center + Search Faceted Path (Development)

## Date
2026-03-06

## Goal
- Continue Day2+ parallel track by:
  - introducing unified ops policy center APIs (`/api/v1/ops/policies/**`);
  - migrating preview diagnostics page batch recovery actions to `/api/v1/ops/recovery/**`;
  - routing advanced frontend search requests through faceted search to leverage ES aggregations and accurate `totalHits`.

## Implemented

### 1) Unified ops policy center (backend)
- Added `OpsPolicyService`:
  - in-memory version snapshots for domain policies;
  - policy update versioning;
  - rollback to previous/target version for domain policy.
- Added `OpsPolicyController`:
  - `GET /api/v1/ops/policies?domain=PREVIEW`
  - `PUT /api/v1/ops/policies/{domain}`
  - `POST /api/v1/ops/policies/{domain}/rollback`
- Added policy audit events:
  - `OPS_POLICY_UPDATED`
  - `OPS_POLICY_ROLLBACK`

### 2) Preview policy registry rollback support
- Extended `PreviewFailurePolicyRegistry` with:
  - `replaceAll(...)` for snapshot restore;
  - internal reusable `defaultPolicies()` initialization path.

### 3) Preview diagnostics UI migration to unified ops APIs
- In `PreviewDiagnosticsPage`:
  - reason-scope queue action switched to `opsRecoveryService.queueByReason(...)`;
  - dead-letter replay action switched to `opsRecoveryService.replayBatch(...)`;
  - failure policy load/save switched to `opsPolicyService`;
  - added policy version chip (`Policy version vN`) in policy panel.

### 4) Search advanced path faceted routing
- Updated frontend `nodeService.searchNodes(...)`:
  - simple query-only path keeps `/search`;
  - advanced filtered path now calls `/search/faceted` with `includeSuggestions`.
- Search response now consumes:
  - `totalHits` for accurate totals;
  - aggregated `facets` payload (if returned).
- Updated `nodeSlice` to store facets from search payload when available.
