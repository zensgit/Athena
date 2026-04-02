# Phase 289 - Odoo Async Governance Overview API + Risk Scoring (Verification)

## Date
- 2026-03-13

## Verification Scope
- Backend:
  - unified async governance overview endpoint aggregation and degraded-state handling
  - risk-level calculation surfacing in API response
- Frontend:
  - Admin Dashboard overview consumes unified endpoint
  - fallback path remains functional at compile/lint level
  - risk/status chips and expanded counters compile and render contracts

## Executed Commands
- Backend targeted test:
  - `cd ecm-core && mvn -q -Dtest=AnalyticsControllerTest test`
- Backend security test:
  - `cd ecm-core && mvn -q -Dtest=AnalyticsControllerSecurityTest test`
- Backend targeted + security combined run:
  - `cd ecm-core && mvn -q -Dtest=AnalyticsControllerTest,AnalyticsControllerSecurityTest test`
- Backend compile:
  - `cd ecm-core && mvn -q -DskipTests compile`
- Frontend lint:
  - `cd ecm-frontend && npm run -s lint -- --max-warnings=0`
- Frontend build:
  - `cd ecm-frontend && npm run -s build`

## Results
- `mvn -q -Dtest=AnalyticsControllerTest test`: passed
- `mvn -q -Dtest=AnalyticsControllerSecurityTest test`: passed
- `mvn -q -Dtest=AnalyticsControllerTest,AnalyticsControllerSecurityTest test`: passed
- `mvn -q -DskipTests compile`: passed
- `npm run -s lint -- --max-warnings=0`: passed
- `npm run -s build`: passed

## Test Notes
- Added unit coverage for unified overview API in `AnalyticsControllerTest`:
  - aggregate counters and domain risk mapping
  - degraded domain propagation to overall status/risk
- Added security coverage in `AnalyticsControllerSecurityTest`:
  - unauthenticated request -> `401`
  - authenticated non-admin -> `403`
  - authenticated admin -> `200`
- Frontend TypeScript build validates:
  - unified response typing
  - fallback aggregation path typing
  - new risk/counter UI rendering path

## Manual Spot Check
- Pending (not executed in this turn):
  - open `/admin` Overview tab and verify:
    - overall status/risk chips
    - per-domain risk/timed-out/expired columns
    - refresh behavior with degraded-domain toast

## Conclusion
- Automated verification status: `passed`
- Delivery status: `phase complete (automation), manual UI spot check pending`
