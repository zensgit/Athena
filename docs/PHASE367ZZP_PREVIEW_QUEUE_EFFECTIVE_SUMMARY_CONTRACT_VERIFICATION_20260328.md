# Phase367ZZP - Preview Queue Effective Summary Contract Verification

## Scope

Validate that preview queue responses now expose rendition-backed effective preview semantics and that high-frequency search surfaces compile against the richer queue status contract.

## Checks

### 1. Backend tests

Command:

```bash
cd ecm-core && mvn -q -Dtest=DocumentControllerPreviewRepairTest test
```

Result:

- Pass

### 2. Frontend lint

Command:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/services/nodeService.ts \
  src/pages/SearchResults.tsx \
  src/pages/AdvancedSearchPage.tsx
```

Result:

- Pass

### 3. Frontend build

Command:

```bash
cd ecm-frontend && npm run -s build
```

Result:

- Pass

### 4. Patch hygiene

Command:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java \
  ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java \
  ecm-frontend/src/services/nodeService.ts \
  ecm-frontend/src/pages/SearchResults.tsx \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  docs/PHASE367ZZP_PREVIEW_QUEUE_EFFECTIVE_SUMMARY_CONTRACT_DEV_20260328.md \
  docs/PHASE367ZZP_PREVIEW_QUEUE_EFFECTIVE_SUMMARY_CONTRACT_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- preview queue responses now expose effective rendition-backed preview semantics
- ordinary search and advanced search reflect queue-triggered preview state changes immediately
