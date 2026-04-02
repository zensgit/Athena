# Phase358 - Async Governance Overview Includes Batch Download (Verification)

## Verification Commands
```bash
cd ecm-core
mvn -q -Dtest=AnalyticsControllerTest,AnalyticsControllerSecurityTest test

cd ../ecm-frontend
npx eslint src/pages/AdminDashboard.tsx
npm run -s build
```

## Result
- PASS

## Covered
- `GET /api/v1/analytics/async-governance/overview` aggregates:
  - audit
  - ops
  - search
  - preview
  - batch download
- aggregate counters reflect the additional batch download domain
- degraded-domain handling escalates correctly when batch download summary lookup fails
- admin security path still holds after the new dependency was added
- `AdminDashboard` async health panel now shows batch download inside the shared domain overview
- frontend typing/lint/build stayed green after the additional domain and wording update

## Notes
- This phase intentionally stays narrow: it unifies overview aggregation first, without yet refactoring the underlying async task implementations into a shared framework.
