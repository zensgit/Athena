# Phase 366B: Advanced Search Single Envelope Verification

## Frontend

- `cd ecm-frontend && npx eslint src/services/nodeService.ts src/pages/AdvancedSearchPage.tsx`
- `cd ecm-frontend && npm run -s build`

## Scope verified

- `AdvancedSearchPage` main search path now performs one unified service call for results/facets/stats/pivot
- existing page state setters continue to receive the same effective data shapes
- frontend compiles successfully after service-layer convergence

## Notes

- This slice does not yet migrate every secondary search workflow in `AdvancedSearchPage`.
- Batch preview retry candidate collection still uses paged `searchNodes(...)` scanning by design.
