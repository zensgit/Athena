# Phase 366: Search Consumer Convergence Verification

## Backend

- `cd ecm-core && mvn -q -Dtest=SearchControllerTest test`

## Frontend

- `cd ecm-frontend && npx eslint src/services/nodeService.ts`
- `cd ecm-frontend && npm run -s build`

## Scope verified

- unified search envelope returns `results + facets + suggestions`
- `searchNodes(...)` consumes `/search/query` without changing its public return shape
- `getAdvancedSearchStats(...)` consumes `/search/query` `stats`
- `getAdvancedSearchPivotStats(...)` consumes `/search/query` `pivot`
- existing page components continue to compile against unchanged service-level return contracts
