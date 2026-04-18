# PR-21C RM Create-Path Guards Verification

## Targeted Backend Verification

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test -Dtest=RecordsManagementServiceTest,BulkImportServiceTest,TransferReceiverServiceTest,LoopbackTransferClientTest
```

Result:

- `34` tests passed
- `0` failures
- `0` errors

Covered checks:

- RM service blocks create targets at file plan root and inside file plan scope
- bulk import rejects governed target folders
- bulk import rejects overwrite of RM-governed existing nodes
- transfer receiver rejects governed target folders
- transfer receiver rejects overwrite of governed existing documents
- loopback replication rejects governed target folders before copy

## Full Backend Regression

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test
```

Result:

- `1533` tests run
- `0` failures
- `0` errors
- `11` skipped

## Static Check

Command:

```bash
git diff --check
```

Result:

- passed
