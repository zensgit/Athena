# Phase 241 - Preview Preflight Resolver + Hash-Enforced Read Repair (Dev)

Date: 2026-03-10  
Scope: `ecm-core` + `ecm-frontend`

## 1. Goals

Complete Day2-7 Stream C/D benchmark items in one slice:

1. Add queue preflight resolver for capability, route, size threshold, and policy-profile mapping.
2. Surface preflight outcomes in search-scope dry-run diagnostics and CSV export.
3. Enforce hash-safe preview read path and provide a repair endpoint for stale rendition state.

## 2. Backend Implementation

## 2.1 Preflight resolver (new component)

File: `ecm-core/src/main/java/com/ecm/core/preview/PreviewPreflightResolver.java`

- Added unified preflight contract:
  - route selection: `cad/pdf/image/office/text/unsupported`
  - capability checks (e.g. CAD enabled + endpoint configured)
  - source-size threshold checks (global + route overrides)
  - policy profile mapping via `PreviewFailurePolicyRegistry`
  - pipeline chain summary for operator diagnostics
- Decline reasons include:
  - `MIME_UNSUPPORTED`
  - `CAD_DISABLED`
  - `CAD_ENDPOINT_UNCONFIGURED`
  - `SOURCE_TOO_LARGE`

## 2.2 Search dry-run + batch preflight integration

File: `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`

- Integrated preflight evaluation inside `collectMatchedRetryableFailures`.
- Declined items are excluded from match set and counted in `skipBreakdown` as:
  - `PREFLIGHT_<REASON>`
- Dry-run sample DTO extended with preflight fields:
  - `preflightStatus`
  - `preflightSkipReason`
  - `preflightRoute`
  - `preflightPolicyProfile`
  - `preflightPipeline`
- CSV export document section now includes preflight columns.
- Queue execution path now consumes preflight decisions from the plan and keeps “declined means skipped” semantics.

## 2.3 Hash-safe read path + repair operation

Files:
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`

Changes:

- Added preview readiness evaluation API (`PreviewReadiness`) in `PreviewService`:
  - `READY_HASH_MATCH`
  - `READY_STALE_HASH`
  - `READY_HASH_UNKNOWN`
  - `ZERO_SOURCE`
  - `NOT_READY`
- Added invalidation API (`invalidateRendition`) and result contract (`PreviewRepairResult`).
- `GET /api/v1/documents/{id}/preview` now enforces hash policy by default:
  - on stale/zero-source, preview generation is withheld
  - stale path can auto-queue forced repair
  - returns explicit failed preview payload with retry hint
- Added repair endpoint:
  - `POST /api/v1/documents/{id}/preview/repair`
  - supports `forceInvalidate`, `requeue`, `forceQueue`
  - returns readiness + invalidation + queue outcome

## 2.4 Config additions

File: `ecm-core/src/main/resources/application.yml`

Added:

- `ecm.preview.preflight.enabled`
- `ecm.preview.preflight.max-source-size-bytes`
- `ecm.preview.preflight.max-source-size-bytes-by-route`
- `ecm.preview.read.hash-enforce.enabled`
- `ecm.preview.read.auto-repair-on-stale`

## 3. Frontend Adaptation

Files:
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

Changes:

- Extended dry-run sample type with preflight fields.
- Advanced search dry-run panel now displays sample-level preflight chips (status/route/profile/decline reason).
- Added `repairPreview(...)` client API and `PreviewRepairStatus` response type.

## 4. Tests Added/Updated

Backend:

- New:
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewPreflightResolverTest.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java`
- Updated:
  - `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`

Frontend:

- Type + UI updates compile-tested via lint/build gates.
