# Phase367ZW Document Preview Rendition Summary Consumption Verification

## Scope

Validate that `DocumentPreview` now prefers rendition summary state and that the shared preview status utility understands rendition-backed registered state.

## Automated Validation

### Frontend lint

Command:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/components/preview/DocumentPreview.tsx \
  src/utils/previewStatusUtils.ts \
  src/utils/previewStatusUtils.test.ts
```

Result:

- Passed

### Frontend unit test

Command:

```bash
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/previewStatusUtils.test.ts
```

Result:

- Passed

Notable assertion added:

- `REGISTERED` is normalized to `PENDING`

### Frontend production build

Command:

```bash
cd ecm-frontend && npm run -s build
```

Result:

- Passed

## Expected Functional Outcome

- Opening the preview dialog now loads node rendition summary alongside lock/checkout metadata.
- Preview chip and alert logic can surface `UNSUPPORTED` and `PENDING` more consistently.
- Preview retry actions remain limited to retryable failed states.

## Residual Gap

This phase improves the single-document preview surface, but it does not yet migrate search/index-based preview status consumers or make `RenditionResource` the complete lifecycle source of truth.
