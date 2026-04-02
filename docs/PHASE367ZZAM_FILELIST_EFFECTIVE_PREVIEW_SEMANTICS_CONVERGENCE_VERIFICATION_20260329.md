# Phase367ZZAM FileList Effective Preview Semantics Convergence Verification

## Scope

Verified browse-level preview chip convergence onto shared effective preview semantics.

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/browser/FileList.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/components/browser/FileList.tsx docs/PHASE367ZZAM_FILELIST_EFFECTIVE_PREVIEW_SEMANTICS_CONVERGENCE_DEV_20260329.md docs/PHASE367ZZAM_FILELIST_EFFECTIVE_PREVIEW_SEMANTICS_CONVERGENCE_VERIFICATION_20260329.md
```

## Result

- ESLint passed for `FileList.tsx`.
- Frontend production build passed.
- `git diff --check` passed for the targeted files.

## Assertions Covered

- Browse list/grid renderers no longer treat raw `previewStatus` as the semantic source.
- Unsupported preview cases now use the same effective failure meta path as other surfaces.
