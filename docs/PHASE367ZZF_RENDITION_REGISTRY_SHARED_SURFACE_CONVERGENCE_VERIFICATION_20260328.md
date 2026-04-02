# Phase367ZZF - Rendition Registry Shared Surface Convergence Verification

## Scope

Validate that `DocumentPreview` and `AdvancedSearchPage` now consume the shared `RenditionDefinitionDialog` instead of remaining tooltip/text-only surfaces.

## Checks

### 1. Frontend lint

Command:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/components/preview/DocumentPreview.tsx \
  src/pages/AdvancedSearchPage.tsx \
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
  ecm-frontend/src/components/preview/DocumentPreview.tsx \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  docs/PHASE367ZZF_RENDITION_REGISTRY_SHARED_SURFACE_CONVERGENCE_DEV_20260328.md \
  docs/PHASE367ZZF_RENDITION_REGISTRY_SHARED_SURFACE_CONVERGENCE_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- `DocumentPreview` now exposes the shared rendition registry dialog from the existing `Renditions N` chip.
- `AdvancedSearchPage` relation details now provide a shared `View rendition registry` entry for the representative document.
- Athena removes another pair of page-specific operator branches and further converges on one rendition-registry surface.
