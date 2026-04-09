# Phase 369BM: Remote Transfer Receiver/Auth Hardening Verification

## Focused verification

### Backend tests

```bash
cd ecm-core
mvn -q -Dtest=TransferReceiverServiceTest,TransferReceiverControllerTest,AthenaTransferHttpClientTest,TransferReplicationServiceTest,TransferReplicationControllerTest test
```

Coverage intent:

- receiver credential matching and subtree authorization
- duplicate folder/document replica naming on the receiver
- temporary internal admin execution path for folder/document creation
- receiver controller HTTP contract and forbidden mapping
- outbound ATHENA_HTTP client migration to `/transfer/receiver/**`
- verification message propagation into transfer target status

### Diff hygiene

```bash
git diff --check
```

## Expected outcomes

- `/api/v1/transfer/receiver/**` is reachable without JWT auth
- receiver secrets are not sent through `Authorization: Bearer ...`
- remote verification uses `/transfer/receiver/verify`
- remote folder/document creation uses dedicated receiver endpoints
- receiver-side duplicate names are suffixed with `(Replica n)`
- invalid receiver credentials return `403`

## Residual limitations

- Receiver auth still reuses outbound `TransferTarget` configuration as the credential source
- No dedicated inbound transfer target registry yet
- No richer transport diagnostics or retry/backoff policy in this phase
