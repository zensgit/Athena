# Phase 209 - Advanced Search Pivot Stats API - Verification

## Date
2026-03-08

## Scope
- Verify `POST /api/v1/search/advanced/stats/pivot` response ordering, matrix shape, and bounded behavior.
- Verify security behavior (anonymous denied, authenticated USER/ADMIN allowed).
- Verify backend compile stability.

## Commands and results

1. Backend targeted tests
```bash
cd ecm-core
mvn -q -Dtest=SearchControllerTest,SearchControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

## Verified outcomes
- Pivot stats endpoint returns stable top buckets and deterministic matrix ordering.
- Endpoint authentication behavior follows `/api/**` policy and matches `/advanced/stats`.
- Internal matrix fan-out is bounded by topN limits, avoiding unbounded query amplification.
