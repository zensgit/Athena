# PHASE367ZZAD Document Preview/Repair Effective Summary Convergence Verification

## Commands
- `cd ecm-core && mvn -q -Dtest=DocumentControllerPreviewRepairTest test`
- `cd ecm-frontend && ./node_modules/.bin/eslint src/services/nodeService.ts src/components/preview/DocumentPreview.tsx`
- `cd ecm-frontend && npm run -s build`

## Assertions Covered
- Hash-enforced stale preview no longer falls back to raw `READY`.
- Zero-source hash enforcement still reports effective `UNSUPPORTED`.
- `preview/repair` returns rendition-backed preview summary fields.
- Frontend shared types compile cleanly against the richer repair contract.

## Result
- Passed.
