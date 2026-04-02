# PHASE367ZZAB Rendition Dialog Mutation Feedback Consumption

## Goal
- Make the shared rendition registry dialog immediately reflect mutation outcomes instead of waiting for a full refresh cycle.

## Scope
- `ecm-frontend/src/components/dialogs/RenditionDefinitionDialog.tsx`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/utils/renditionDefinitionUtils.ts`
- `ecm-frontend/src/utils/renditionDefinitionUtils.test.ts`

## Design
- Extend `NodeRenditionMutationResponse` with `previewSummary`.
- Add pure helpers to:
  - patch the mutated definition back into local dialog state
  - format a concise operator-facing mutation summary
- Show an inline success alert after mutation.
- Keep the existing backend refetch as reconciliation, but stop forcing operators to wait for it to understand the outcome.

## Outcome
- All pages that use `RenditionDefinitionDialog` now benefit from the richer contract.
- Mutation feedback is consistent across browse/search/preview/upload/admin surfaces.
