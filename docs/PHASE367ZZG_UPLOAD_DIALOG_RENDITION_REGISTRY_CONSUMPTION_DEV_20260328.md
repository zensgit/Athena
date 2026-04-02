# Phase367ZZG - Upload Dialog Rendition Registry Consumption

## Goal

Extend the definition-backed rendition operator surface into the upload workflow so freshly uploaded documents no longer remain on a preview-only status island.

## Why

Recent phases converged the shared rendition registry dialog across browse, ordinary search, document preview, and advanced search relation details.

`UploadDialog` was still lagging:

- it only showed preview status chips
- it still exposed raw-ish fallback text such as `previewFailureReason || previewStatus || 'Preview pending'`
- there was no direct way to inspect rendition definitions for a newly uploaded document

That left the upload workflow as a separate rendition operator branch.

## Design

File: `ecm-frontend/src/components/dialogs/UploadDialog.tsx`

### 1. Reuse the shared registry dialog

- Import `RenditionDefinitionDialog`
- add `renditionDialogNode` state
- mount the shared dialog at the root of the upload dialog

### 2. Add per-uploaded-item registry access

For uploaded document rows:

- keep the existing preview status chip
- add a `Rendition Registry` action beside it
- open the shared dialog for that uploaded node

This keeps the upload list lightweight while giving operators the same definition-backed drill-down used elsewhere.

### 3. Normalize uploaded-item secondary preview text

Replace raw fallback text with effective preview semantics:

- `FAILED` / `UNSUPPORTED` rows prefer `previewFailureReason`, otherwise the effective preview label
- all other rows show the effective preview label

This removes another place where the upload UI leaked raw preview field values instead of the converged interpretation.

## Result

After this phase, the upload workflow joins the same rendition-registry operator path already used in search, browse, preview, and advanced search surfaces.
