# Phase367ZZF - Rendition Registry Shared Surface Convergence

## Goal

Continue the rendition-definition line by removing the remaining inline-only operator summaries in `DocumentPreview` and `AdvancedSearchPage`, and routing those surfaces through the shared `RenditionDefinitionDialog`.

## Why

Phase367ZZE introduced a shared rendition registry dialog for browse and ordinary search, but two high-frequency operator surfaces still used separate inline-only summaries:

- `DocumentPreview` exposed a tooltip-only `Renditions N` chip.
- `AdvancedSearchPage` relation details exposed a text summary without the shared dialog.

That left Athena with two parallel operator surfaces for the same definition-backed rendition contract.

## Design

### 1. Document preview consumes the shared dialog

File: `ecm-frontend/src/components/preview/DocumentPreview.tsx`

- Import `RenditionDefinitionDialog`.
- Add a local `renditionDefinitionDialogOpen` state.
- Make the existing `Renditions N` chip clickable.
- Keep the existing tooltip summary for fast hover triage.
- Mount `RenditionDefinitionDialog` beside the existing `CheckoutGraphDialog`, keyed to the previewed node.

This preserves the lightweight hover experience while giving preview operators a full registry drill-down without leaving the dialog.

### 2. Advanced search relation details converge on the same surface

File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

- Import `RenditionDefinitionDialog`.
- Add `renditionDialogNode` state to freeze the representative document used for the dialog.
- Keep the existing inline rendition registry summary in relation details.
- Add a `View rendition registry` action under that summary.
- Mount the shared dialog near the existing checkout graph dialog.

Using a node snapshot instead of a bare boolean avoids coupling the dialog to future changes in the representative document while it is open.

## Result

After this phase, the definition-backed rendition operator surface is shared across:

- ordinary search
- file browser
- document preview
- advanced search relation details

This reduces UI drift and moves Athena closer to a single rendition-registry control surface rather than per-page one-off summaries.
