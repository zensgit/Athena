# Phase367ZZQ - Upload Dialog Preview Queue Summary Consumption Verification

## Scope

Validate that the upload dialog compiles against the richer preview queue status contract.

## Checks

### 1. Frontend lint

Command:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/components/dialogs/UploadDialog.tsx \
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
  ecm-frontend/src/components/dialogs/UploadDialog.tsx \
  docs/PHASE367ZZQ_UPLOAD_DIALOG_PREVIEW_QUEUE_SUMMARY_CONSUMPTION_DEV_20260328.md \
  docs/PHASE367ZZQ_UPLOAD_DIALOG_PREVIEW_QUEUE_SUMMARY_CONSUMPTION_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- upload rows now retain queue-returned preview failure semantics immediately after queueing
