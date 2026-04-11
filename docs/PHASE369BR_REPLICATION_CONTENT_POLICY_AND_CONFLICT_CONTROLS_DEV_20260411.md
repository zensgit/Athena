# Phase 369BR: Replication Content Policy And Conflict Controls - Frontend Dev

## Scope
- Added operator-facing conflict policy support to the transfer replication definition surface.
- Kept the change frontend-only and aligned it with the current backend contract, which does not yet persist this field.

## Changed Files
- `ecm-frontend/src/pages/TransferReplicationPage.tsx`
- `ecm-frontend/src/services/transferReplicationService.ts`
- `ecm-frontend/src/services/transferReplicationService.test.ts`

## Notes
- The definition dialog now exposes `SKIP`, `RENAME`, and `OVERWRITE`.
- The definitions table shows a conflict-policy summary chip so operators can see the active intent at a glance.
- The request builder includes the conflict policy field so the UI can send it with definition mutations without changing backend files.
