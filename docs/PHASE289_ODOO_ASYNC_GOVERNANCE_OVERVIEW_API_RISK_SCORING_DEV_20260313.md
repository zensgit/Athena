# Phase 289 - Odoo Async Governance Overview API + Risk Scoring (Dev)

## Date
- 2026-03-13

## Goal
- Deliver a unified async governance overview API for Admin Dashboard.
- Move cross-center health aggregation from frontend-only stitching to backend-governed aggregation.
- Add explicit risk scoring (LOW/MEDIUM/HIGH/CRITICAL) and degraded-domain signaling.

## Benchmark Alignment (Alfresco Surpass Track)
- Governance control-plane parity target:
  - one endpoint to summarize async export health across domains,
  - explicit lifecycle counts and failure pressure,
  - operator-facing risk signal for triage.
- Surpass point:
  - backend risk computation + frontend fallback path retained for resilience.

## Implementation Scope

### Backend
- File: `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- Added endpoint:
  - `GET /api/v1/analytics/async-governance/overview`
- Domain aggregation sources:
  - audit: internal async audit export summary
  - ops: `OpsRecoveryController` async export summary
  - search: `SearchController` async dry-run export summary
  - preview: `PreviewDiagnosticsController` rendition resources async export summary
- New response model includes:
  - generatedAt / overallStatus / overallRiskLevel
  - totalDomains / degradedDomainCount
  - global counters: total/active/terminal/queued/running/completed/cancelled/failed/timedOut/expired
  - global failureRate
  - per-domain status/risk/error + lifecycle counters
- Risk model:
  - `HIGH` when timeout/expired appears, high active pressure, or high failure rate
  - `MEDIUM` for moderate active pressure / failure / cancellation pressure
  - `LOW` otherwise
  - degraded domain escalates overall risk to `CRITICAL`

### Backend Tests
- File: `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
- Added tests:
  - `asyncGovernanceOverviewAggregatesCrossCenterSummaries`
  - `asyncGovernanceOverviewMarksDegradedDomains`
- Coverage focus:
  - cross-domain aggregation correctness
  - degraded domain propagation to overall status/risk
- Security hardening test file:
  - `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerSecurityTest.java`
  - validates authZ gate for unified governance endpoint (`401/403/200` path)

### Frontend
- File: `ecm-frontend/src/pages/AdminDashboard.tsx`
- Updated Async Export Health Overview behavior:
  - first attempts unified endpoint: `/analytics/async-governance/overview`
  - keeps prior multi-endpoint aggregation as fallback if unified call fails
- Added health/risk model fields:
  - domain risk level chip
  - overall status chip (HEALTHY/DEGRADED)
  - overall risk chip (LOW/MEDIUM/HIGH/CRITICAL)
  - additional counters shown: queued/running/timedOut/expired + aggregate failure%
- UI table enhancements:
  - added `Risk`, `Timed Out`, `Expired` columns

## Compatibility & Rollout Notes
- Backward compatible with existing center-specific summary APIs.
- Frontend fallback ensures legacy/partial backend deployments still render overview.
- No public API removals or schema-breaking changes introduced.

## Risks / Mitigations
- Risk: one domain summary endpoint fails intermittently.
  - Mitigation: degraded-domain marking + fallback aggregation path.
- Risk: runtime coupling to multiple controllers.
  - Mitigation: guarded exception handling per domain; partial failure does not break whole overview.
