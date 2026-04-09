# Phase 369BO: Transfer Replication Diagnostics And Retry Controls Verification

## Focused verification

### Backend tests

```bash
cd ecm-core
mvn -q -Dtest=TransferReplicationServiceTest,TransferReplicationControllerTest,TransferReceiverRegistryServiceTest,TransferReceiverControllerTest,AthenaTransferHttpClientTest test
```

Coverage intent:

- successful jobs persist transport success diagnostics
- failed jobs persist transport failure diagnostics
- retry creates a new queued/executed job with retry lineage and incremented attempt number
- controller contract exposes retry endpoint and expanded job payload
- no regression in existing transfer receiver and outbound HTTP seams

### Diff hygiene

```bash
git diff --check
```

## Expected outcomes

- replication jobs expose last transport status/message and last attempted timestamp
- failed transport runs can be retried without destroying prior job history
- retried jobs reference the original failed/canceled job
- successful retries complete with updated transport diagnostics

## Residual limitations

- Retry is manual only
- Diagnostics are still lightweight and not a full event trail
- No frontend/operator retry controls in this phase
