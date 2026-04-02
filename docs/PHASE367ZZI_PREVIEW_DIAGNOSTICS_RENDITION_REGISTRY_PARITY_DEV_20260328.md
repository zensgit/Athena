# Phase367ZZI - Preview Diagnostics Rendition Registry Parity

## Goal

Finish the rendition-registry operator convergence inside `PreviewDiagnosticsPage` so the remaining diagnostics tables no longer stop at preview-state text and ad-hoc actions.

## Why

Phase367ZZH already added registry drill-down to:

- queue diagnostics
- queue declined
- rendition resources

But the same page still had four important diagnostics surfaces with document identity and actions, yet no shared registry entry:

- preview failure ledger
- preview dead-letter queue
- rendition prevention registry
- preview failures table

That left the admin diagnostics experience only partially converged.

## Design

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

Reuse the existing:

- `renditionDialogTarget`
- `handleOpenRenditionRegistry(...)`
- shared `RenditionDefinitionDialog`

Then add `Registry` actions to the four remaining tables:

### 1. Failure ledger

Add `Registry` beside `Reset` so operators can inspect rendition definition/applicability before clearing ledger state.

### 2. Dead-letter queue

Add `Registry` beside `Replay` / `Clear` so operators can inspect the underlying rendition definition before acting on a dead-letter entry.

### 3. Rendition prevention registry

Add `Registry` beside `Unblock` / `Requeue` so policy-driven prevention entries can be checked against the actual registered rendition definition.

### 4. Preview failures table

Add `Registry` to the existing icon/button action row so failed preview samples can jump directly into rendition definition state from the main failure table.

## Result

After this phase, `PreviewDiagnosticsPage` exposes rendition-registry drill-down across all major preview governance tables instead of only a subset.
