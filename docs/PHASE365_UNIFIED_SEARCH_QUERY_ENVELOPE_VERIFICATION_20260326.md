# Phase 365: Unified Search Query Envelope Verification

## Backend

- `cd ecm-core && mvn -q -Dtest=SearchControllerTest test`

## Scope verified

- `/api/v1/search/query` returns combined response sections for `results/context/stats`
- `includeRequest=true` returns normalized request echo
- pivot-only mode works without running full-text result search
- legacy `/advanced/context/stats/pivot` endpoints still compile and execute through shared helper paths

## Notes

- This verification slice is intentionally backend-only.
- Frontend migration to the new envelope is deferred to the next search convergence phase.
