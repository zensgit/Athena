# Phase 234 - Async Export Health Overview (Admin UI) - Development

## Date
2026-03-09

## Goal
- 在管理台提供跨域异步导出任务健康总览，统一观测 Audit / Ops Recovery / Search / Preview 四条链路。
- 在不改后端接口的前提下，以最小改动接入并行聚合与降级容错。

## Implemented

### 1) Admin dashboard unified health overview
- Updated `ecm-frontend/src/pages/AdminDashboard.tsx`
  - Added domain config for four summary endpoints:
    - `/analytics/audit/export-async/summary`
    - `/ops/recovery/history/export-async/summary`
    - `/search/preview/queue-failed/dry-run/export-async/summary`
    - `/preview/diagnostics/renditions/resources/export-async/summary`
  - Added normalized summary model:
    - `AsyncExportHealthSummary`
    - `AsyncExportHealthDomainState`
  - Added compatibility normalizer for payload field variants:
    - supports `totalCount/activeCount/...`
    - supports `total/active/...`

### 2) Parallel fetch + failure isolation
- Added `loadAsyncExportHealthOverview(silent?)`:
  - uses `Promise.allSettled` to request all four domains in parallel.
  - any failed domain is marked as `degraded` with `failed-to-load`.
  - successful domains remain visible and aggregated.
  - does not block existing dashboard modules.

### 3) New UI section
- Added `Async Export Health Overview` card in Overview tab:
  - aggregate chips: `Total`, `Active`, `Terminal`, `Completed`, `Failed`, `Cancelled`
  - per-domain table showing status and same counters
  - `Refresh async export health overview` action
  - `Last updated` timestamp
- Wired into existing refresh behaviors:
  - initial load in `useEffect`
  - top-right dashboard refresh also refreshes health overview

### 4) Mocked e2e extension
- Updated `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`
  - added mocks for:
    - `/ops/recovery/history/export-async/summary`
    - `/search/preview/queue-failed/dry-run/export-async/summary`
    - `/preview/diagnostics/renditions/resources/export-async/summary`
  - added assertions for:
    - overview heading visibility
    - aggregate chips (`Total/Active/Terminal`)
    - refresh button triggers summary re-fetch calls

## Impact
- 管理员可在单页快速判断四域异步导出总体健康状态，不再需要在多个页面切换对比。
- 四域任务治理观测能力进一步统一，为后续跨域任务中心与告警策略提供稳定基础。
