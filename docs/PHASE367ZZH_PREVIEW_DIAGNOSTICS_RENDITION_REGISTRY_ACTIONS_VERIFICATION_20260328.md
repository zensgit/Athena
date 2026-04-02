# Phase367ZZH - Preview Diagnostics Rendition Registry Actions Verification

## Scope

Validate that `PreviewDiagnosticsPage` now exposes the shared rendition registry dialog from its queue and rendition-resource tables.

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
  docs/PHASE367ZZH_PREVIEW_DIAGNOSTICS_RENDITION_REGISTRY_ACTIONS_DEV_20260328.md \
  docs/PHASE367ZZH_PREVIEW_DIAGNOSTICS_RENDITION_REGISTRY_ACTIONS_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- Queue diagnostics rows can open rendition registry drill-down directly.
- Queue declined rows can open rendition registry drill-down directly.
- Rendition resources rows can open rendition registry drill-down directly.
- Athena removes another operator-only preview/rendition branch and makes admin diagnostics more actionable.
