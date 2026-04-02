# Phase367ZZH - Preview Diagnostics Rendition Registry Actions

## Goal

Bring the shared definition-backed rendition registry surface into the administrator-facing preview diagnostics workbench.

## Why

Athena already exposed `RenditionDefinitionDialog` across browse, ordinary search, advanced search, preview, and upload flows.  
`PreviewDiagnosticsPage` was still lagging behind even though it is one of the highest-value operator surfaces for preview/rendition triage.

Before this phase, diagnostics tables only exposed:

- preview status chips
- decline/queue context
- retry / force actions

They did not expose a direct drill-down into rendition definition state, applicability, dependency chain, or availability.

## Design

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

### 1. Add a shared rendition registry target state

- import `RenditionDefinitionDialog`
- add `renditionDialogTarget`
- add `handleOpenRenditionRegistry(documentId, name)`

This keeps the dialog target stable and reusable across multiple diagnostics tables.

### 2. Add registry actions to queue diagnostics and queue declined tables

For rows with a `documentId`:

- keep existing mime / preview chips
- add `View rendition registry` directly inside the document cell

This lets operators jump from queue governance state to definition/applicability state without leaving the diagnostics page.

### 3. Add registry action to rendition resources table

Inside the existing actions column:

- add `Registry`
- keep existing `Retry` / `Force` actions

This is especially useful when a rendition resource is sampled as failed, unsupported, or stale and the operator needs to inspect the registered definition rather than only the sampled resource row.

## Result

After this phase, Athena’s preview diagnostics workbench is no longer limited to raw preview chips and action buttons; it can drill directly into the same shared rendition registry surface used elsewhere in the product.
