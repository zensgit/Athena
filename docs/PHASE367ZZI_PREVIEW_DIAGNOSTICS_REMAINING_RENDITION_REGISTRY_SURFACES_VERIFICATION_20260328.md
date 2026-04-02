# Phase367ZZI - Preview Diagnostics Remaining Rendition Registry Surfaces Verification

## Scope

Validate that the remaining document-centric diagnostics tables in `PreviewDiagnosticsPage` now expose the shared rendition registry dialog.

## Checks

### 1. Frontend lint

Command:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/PreviewDiagnosticsPage.tsx
```

Result:

- Pass

### 2. Frontend build

Command:

```bash
cd ecm-frontend && npm run -s build
```

Result:

- Pass

### 3. Patch hygiene

Command:

```bash
git diff --check -- \
  ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx \
  docs/PHASE367ZZI_PREVIEW_DIAGNOSTICS_REMAINING_RENDITION_REGISTRY_SURFACES_DEV_20260328.md \
  docs/PHASE367ZZI_PREVIEW_DIAGNOSTICS_REMAINING_RENDITION_REGISTRY_SURFACES_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- Failure ledger rows can open rendition registry directly.
- Dead-letter rows can open rendition registry directly.
- Prevention registry rows can open rendition registry directly.
- Preview failure sample rows can open rendition registry directly.
- Athena reduces another set of operator detours inside the admin diagnostics workflow.
