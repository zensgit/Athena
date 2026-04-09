# Phase 369BL: Transfer Replication Operator Surface Verification

> **Date**: 2026-04-09

## Verification

### Menu contract test

```bash
cd ecm-frontend
CI=true npm test -- --watch=false --runInBand src/components/layout/MainLayout.menu.test.tsx
```

### Lint and build

```bash
cd ecm-frontend
./node_modules/.bin/eslint src/pages/TransferReplicationPage.tsx src/services/transferReplicationService.ts src/App.tsx src/components/layout/MainLayout.tsx src/components/layout/MainLayout.menu.test.tsx
npm run -s build
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- The admin menu exposes `Transfer Replication` for admins.
- Existing admin and viewer menu expectations still pass.
- The route and page build cleanly.
- The operator surface covers transfer targets, replication definitions,
  verification, and run actions.

## Notes

- Existing unrelated frontend build warnings may still appear and should be
  treated separately if they predate this phase.
