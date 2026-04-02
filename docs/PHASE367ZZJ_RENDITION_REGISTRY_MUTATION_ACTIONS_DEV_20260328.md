# Phase367ZZJ - Rendition Registry Mutation Actions

## Goal

Promote the shared rendition registry dialog from a read-only drill-down surface into a lightweight operator control surface.

## Why

Athena already exposed the shared `RenditionDefinitionDialog` across multiple work surfaces, but it still behaved like a passive inspector:

- open rendition content
- inspect state, dependency, applicability, generation mode

That left a gap between what the backend already supported and what operators could actually do from the shared registry surface. The backend already exposes:

- `POST /api/v1/nodes/{nodeId}/renditions/{renditionKey}/requeue`
- `POST /api/v1/nodes/{nodeId}/renditions/{renditionKey}/invalidate`

This phase closes that gap.

## Design

### 1. Add service-layer mutation methods

File: `ecm-frontend/src/services/nodeService.ts`

- add `NodeRenditionMutationResponse`
- add `requeueNodeRendition(nodeId, renditionKey, force?)`
- add `invalidateNodeRendition(nodeId, renditionKey, { reason?, requeue?, forceQueue? })`

This keeps rendition mutation semantics in the shared node service rather than page-specific ad hoc API calls.

### 2. Add mutation controls to the shared dialog

File: `ecm-frontend/src/components/dialogs/RenditionDefinitionDialog.tsx`

- add per-definition action state
- add `Requeue`
- add `Invalidate + Requeue`
- refresh definition data after successful mutation
- show toast feedback on success and failure

### 3. Keep actions scoped and safe

Mutation controls are only shown for definitions that are both:

- `registered`
- `applicable`

This avoids surfacing mutation affordances for unsupported or non-applicable definitions.

## Result

After this phase, every page that already uses `RenditionDefinitionDialog` automatically gains a lightweight shared rendition operator surface, not just a read-only registry.
