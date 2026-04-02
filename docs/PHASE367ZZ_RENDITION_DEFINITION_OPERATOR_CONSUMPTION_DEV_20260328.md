# Phase367ZZ Rendition Definition Operator Consumption

## Goal

Turn Athena's new rendition definition registry from a backend-only surface into an operator-visible capability.

This phase wires the registry into two existing high-frequency work surfaces:

- `DocumentPreview`
- `AdvancedSearchPage` node relations details

## Why This Slice

Phase367ZY established a registry-backed API:

- `/api/v1/nodes/{nodeId}/renditions`
- `/api/v1/nodes/{nodeId}/renditions/definitions`

But Athena still had a product gap: operators could not see registry-backed applicability, generation mode, or definition dependencies in the main UI surfaces where they triage rendition issues.

This phase closes that gap without introducing a new standalone diagnostics page.

## Implementation

### 1. Added frontend contract for rendition definitions

`nodeService` now exposes:

- `NodeRenditionDefinitionStatus`
- `getNodeRenditionDefinitions(nodeId)`

This maps directly onto the new definition registry endpoint.

### 2. Added shared rendition definition formatter utilities

New shared helper:

- `src/utils/renditionDefinitionUtils.ts`

It provides:

- generation mode labeling
- operator-friendly definition state labeling
- ordered definition line formatting
- compact summary formatting

This avoids reintroducing surface-specific wording drift.

### 3. Document preview now exposes registry-backed rendition detail

`DocumentPreview` now loads definition metadata together with:

- node details
- lock info
- checkout info
- checkout graph
- rendition summary

The preview toolbar now shows a `Renditions N` chip when definitions are available. Its tooltip exposes ordered definition lines such as:

- `Preview ready • via preview pipeline`
- `Thumbnail pending • via preview-derived • depends on preview`
- `Preview not applicable • ...`

This keeps the surface lightweight while still making registry detail directly available during preview triage.

### 4. Advanced search relations now expose registry-backed rendition summary

The representative-document relations detail flow now also loads `getNodeRenditionDefinitions(...)`.

`Node Relations Summary -> Relation Details` now includes a `Rendition registry:` line, separate from the existing legacy `Renditions:` line.

This matters because the legacy line shows realized relation status, while the registry line shows:

- registered definitions
- applicability
- generation mode
- dependency semantics

## Files

- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/utils/renditionDefinitionUtils.ts`
- `ecm-frontend/src/utils/renditionDefinitionUtils.test.ts`
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

## Operator Impact

Athena operators can now answer questions that were previously hidden behind backend-only detail:

- is this rendition registered but still pending?
- is it not applicable rather than simply not created?
- is it preview-derived?
- does it depend on another rendition?

That makes Athena's rendition operator detail meaningfully stronger than a plain preview-status chip model.

## Residual Gap

This phase consumes the definition registry on two major surfaces, but Athena still has remaining structural work before the rendition line is fully settled:

- more surfaces can still migrate from legacy relation-only semantics
- search/index preview projections still rely on older preview-field semantics
- `RenditionResource` is still not the sole lifecycle source of truth
