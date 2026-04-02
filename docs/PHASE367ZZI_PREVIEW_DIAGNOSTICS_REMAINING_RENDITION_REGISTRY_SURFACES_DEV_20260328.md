# Phase367ZZI - Preview Diagnostics Remaining Rendition Registry Surfaces

## Goal

Finish the next batch of rendition-registry convergence inside `PreviewDiagnosticsPage` by covering the remaining document-centric diagnostics tables that still lacked direct registry drill-down.

## Why

Phase367ZZH added shared registry access to:

- queue diagnostics
- queue declined
- rendition resources

But the page still had several administrator-facing tables where operators had a document id and preview/rendition governance context without a direct path into the shared rendition registry:

- failure ledger
- dead-letter queue
- rendition prevention registry
- preview failure samples

Those remaining gaps forced operators to jump to another page or infer definition/applicability indirectly.

## Design

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

Reuse the existing `handleOpenRenditionRegistry(...)` and `RenditionDefinitionDialog`.

### 1. Failure ledger

- keep the existing name cell
- add `View rendition registry` directly under the document label

### 2. Dead-letter queue

- keep the existing document label
- add `View rendition registry` directly under it

### 3. Rendition prevention registry

- keep name and path
- add `View rendition registry` under the path

### 4. Preview failure samples

- keep name and path
- add `View rendition registry` under the path

Using document-cell placement instead of action-column placement keeps the action close to the identity/context block and avoids overloading already dense action stacks.

## Result

After this phase, `PreviewDiagnosticsPage` exposes shared rendition registry drill-down across nearly all document-centric preview governance tables, not just a subset of them.
