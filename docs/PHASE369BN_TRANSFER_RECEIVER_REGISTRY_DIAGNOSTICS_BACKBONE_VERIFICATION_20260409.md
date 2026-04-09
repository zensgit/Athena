# Phase 369BN: Transfer Receiver Registry And Diagnostics Backbone Verification

## Focused verification

### Backend tests

```bash
cd ecm-core
mvn -q -Dtest=TransferReceiverRegistryServiceTest,TransferReceiverRegistryControllerTest,TransferReceiverServiceTest,TransferReceiverControllerTest,AthenaTransferHttpClientTest,TransferReplicationServiceTest test
```

Coverage intent:

- receiver registry CRUD and duplicate-name validation
- root-folder verification and verification status persistence
- runtime receiver auth against registry credentials instead of outbound targets
- receiver last-access success/failure diagnostics
- registry controller HTTP contract for list/create/update/verify/delete
- no regression in outbound `ATHENA_HTTP` verification/transfer flow

### Diff hygiene

```bash
git diff --check
```

## Expected outcomes

- inbound receiver auth is backed by dedicated `transfer_receivers` rows
- receiver verification persists status, message, and timestamp
- receiver runtime access persists success/failure diagnostics
- `/api/v1/transfer/receivers/**` exposes admin CRUD and verify operations
- outbound remote transfer continues to use `/api/v1/transfer/receiver/**` for runtime folder/document transport

## Residual limitations

- Receiver registry is backend-only; there is no admin UI yet
- Diagnostics are lightweight and do not include retry history or structured event trails
- Receiver secrets are still stored in local persistence without external secret-management integration
