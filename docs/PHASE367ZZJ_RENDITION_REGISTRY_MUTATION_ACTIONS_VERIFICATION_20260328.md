# Phase367ZZJ - Rendition Registry Mutation Actions Verification

## Scope

Validate that the shared rendition registry dialog now supports safe mutation actions and that the service layer exposes the corresponding APIs.

## Checks

### 1. Frontend lint

Command:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/components/dialogs/RenditionDefinitionDialog.tsx \
  src/services/nodeService.ts
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
  ecm-frontend/src/components/dialogs/RenditionDefinitionDialog.tsx \
  ecm-frontend/src/services/nodeService.ts \
  docs/PHASE367ZZJ_RENDITION_REGISTRY_MUTATION_ACTIONS_DEV_20260328.md \
  docs/PHASE367ZZJ_RENDITION_REGISTRY_MUTATION_ACTIONS_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- The shared rendition registry dialog now supports `Requeue`.
- The shared rendition registry dialog now supports `Invalidate + Requeue`.
- All pages already wired to the dialog inherit those actions automatically.
