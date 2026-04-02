# Phase367ZZI - Preview Diagnostics Rendition Registry Parity Verification

## Scope

Validate that the remaining high-value diagnostics tables on `PreviewDiagnosticsPage` now expose shared rendition-registry actions.

## Checks

### 1. Frontend lint

Command:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/pages/PreviewDiagnosticsPage.tsx \
  src/components/dialogs/RenditionDefinitionDialog.tsx
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
  docs/PHASE367ZZI_PREVIEW_DIAGNOSTICS_RENDITION_REGISTRY_PARITY_DEV_20260328.md \
  docs/PHASE367ZZI_PREVIEW_DIAGNOSTICS_RENDITION_REGISTRY_PARITY_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- Failure ledger rows can open rendition registry.
- Dead-letter rows can open rendition registry.
- Prevention registry rows can open rendition registry.
- Preview failure rows can open rendition registry.
- The preview diagnostics workbench is materially closer to a single shared rendition-governance surface.
