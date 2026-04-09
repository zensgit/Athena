# Phase 369BP: Transfer Receiver Registry Operator Surface Verification

## Focused verification

### Frontend checks

```bash
cd ecm-frontend
./node_modules/.bin/eslint src/pages/TransferReplicationPage.tsx src/services/transferReplicationService.ts src/services/transferReplicationService.test.ts
CI=true npm test -- --watch=false --runInBand src/services/transferReplicationService.test.ts
npm run -s build
```

### Diff hygiene

```bash
git diff --check
```

## Expected outcomes

- Admins can list, create, edit, verify, and delete receiver registry entries
- Receiver verification and last-access diagnostics are visible in the transfer admin page
- The page continues to load targets, definitions, jobs, and receiver registry in one workspace

## Residual limitations

- Root folder entry is still manual UUID input
- Receiver diagnostics are summarized fields, not a full audit trail
