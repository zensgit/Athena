# Phase358 - Async Governance Overview Includes Batch Download (Dev)

## Background
- Athena mainline priority has shifted from page-by-page parity to platform consolidation.
- The repository already had a cross-center async governance overview in `AnalyticsController`, but it only covered:
  - audit
  - ops recovery
  - search
  - preview
- Batch download already had its own async task center and summary API, but was still outside the unified governance overview.

## Goal
Bring batch download into the shared async governance overview so the control plane starts covering all major async task domains already present in Athena.

## Implementation
### 1) Backend overview expansion
- Updated `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- Added `BatchDownloadController` as a dependency of the analytics overview controller.
- Expanded `GET /api/v1/analytics/async-governance/overview` to include a new `batchDownload` domain.
- Reused the existing batch download summary contract rather than inventing another task-summary path.
- Kept lifecycle aggregation consistent with other healthy domains:
  - `total`
  - `queued`
  - `running`
  - `completed`
  - `cancelled`
  - `failed`
  - derived `active`
  - derived `terminal`

### 2) Contract wording
- Broadened the endpoint description from “async export governance” toward “async task governance” because batch download is not just an export concern.
- Kept the existing response shape stable to avoid unnecessary churn for current consumers.

### 3) Test coverage
- Updated `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
  - overview aggregate test now asserts 5 domains and includes `batchDownload`
  - degraded-domain test now verifies batch download degradation handling
- Updated `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerSecurityTest.java`
  - admin access path now includes mocked batch download summary lookup

### 4) Frontend overview wiring
- Updated `ecm-frontend/src/pages/AdminDashboard.tsx`
- Added `batchdownload` into the async health domain registry:
  - endpoint: `/nodes/download/batch-async/summary`
- Renamed panel wording from “Async Export Health Overview” to “Async Task Health Overview”
  because the panel now spans both export-style async flows and batch download task lifecycle health.
- Reused the existing normalization path so batch download summary payloads slot into the same aggregate chips/table without extra bespoke UI logic.

## Why This Slice First
- Low conflict: no need to refactor the giant preview/search controllers first
- High leverage: immediately strengthens the shared admin control-plane contract
- Strategic fit: advances the new roadmap direction without starting a risky broad rewrite

## Follow-up
1. Extract a shared async task summary model so overview aggregation no longer depends on controller-specific response records.
2. Introduce a shared adapter layer so overview aggregation stops depending on controller-specific nested response records.
3. Continue expanding from “overview aggregation” into a true unified async task framework.
