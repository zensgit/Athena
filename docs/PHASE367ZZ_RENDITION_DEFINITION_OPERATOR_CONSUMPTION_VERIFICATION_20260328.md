# Phase367ZZ Rendition Definition Operator Consumption Verification

## Scope

Validate that Athena now surfaces registry-backed rendition definition detail in `DocumentPreview` and `AdvancedSearchPage`.

## Automated Validation

### Frontend lint

Command:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/components/preview/DocumentPreview.tsx \
  src/pages/AdvancedSearchPage.tsx \
  src/services/nodeService.ts \
  src/utils/renditionDefinitionUtils.ts \
  src/utils/renditionDefinitionUtils.test.ts
```

Result:

- Passed

### Frontend unit test

Command:

```bash
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/renditionDefinitionUtils.test.ts
```

Result:

- Passed

Covered behaviors:

- generation mode labels are stable
- registry `REGISTERED` maps to operator-facing `pending`
- `not applicable` takes precedence over raw state
- dependency and applicability reason formatting are preserved
- ordered summary/truncation remains stable

### Frontend build

Command:

```bash
cd ecm-frontend && npm run -s build
```

Result:

- Passed

### Diff hygiene

Command:

```bash
git diff --check -- \
  ecm-frontend/src/services/nodeService.ts \
  ecm-frontend/src/utils/renditionDefinitionUtils.ts \
  ecm-frontend/src/utils/renditionDefinitionUtils.test.ts \
  ecm-frontend/src/components/preview/DocumentPreview.tsx \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  docs/PHASE367ZZ_RENDITION_DEFINITION_OPERATOR_CONSUMPTION_DEV_20260328.md \
  docs/PHASE367ZZ_RENDITION_DEFINITION_OPERATOR_CONSUMPTION_VERIFICATION_20260328.md
```

Result:

- Pending until after doc write, then expected to pass

## Expected Functional Outcome

- `DocumentPreview` shows a registry-backed `Renditions` chip with tooltip detail
- `AdvancedSearchPage` relation details show a `Rendition registry:` summary line
- registry-backed applicability/dependency/generation semantics are no longer backend-only

## Residual Gap

Athena now exposes the registry on key operator surfaces, but still has remaining rendition work:

- additional consumer surfaces can converge onto definitions
- search/index preview projections still need further migration
- lifecycle ownership is still shared with legacy `Document.preview*` fields
