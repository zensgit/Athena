# Phase 369BP: Transfer Receiver Registry Operator Surface

## Goal

Expose the new transfer receiver registry through the existing transfer replication admin page so operators can manage inbound receiver roots and diagnostics without using raw APIs.

## Scope

- Extend the transfer admin page with a receiver registry section
- Add receiver CRUD and verify service wrappers on the frontend
- Surface receiver verification and last-access diagnostics
- Keep the experience inside the existing transfer replication workspace

## Files

- `ecm-frontend/src/pages/TransferReplicationPage.tsx`
- `ecm-frontend/src/services/transferReplicationService.ts`
- `ecm-frontend/src/services/transferReplicationService.test.ts`

## Out of scope

- Separate navigation entry for receiver registry
- Advanced filtering or audit history for receiver diagnostics
- Rich folder picker UX for root folder selection
