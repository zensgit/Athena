# P4 PR-21A RM Policy Extensions Foundation Verification

## Targeted Backend Verification

Command:

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test \
  -Dtest=RecordsManagementServiceTest,RecordsManagementControllerTest,TrashServiceRecordDeclarationTest
```

Result:

- `21` tests passed
- `0` failures
- `0` errors

Coverage in this slice:

- RM operations telemetry summary and classification
- RM operations controller payload
- restore-from-trash guard on RM-governed content

## Full Backend Regression

Command:

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test
```

Result:

- `1526` tests passed
- `0` failures
- `0` errors
- `11` skipped

## Manual Behavior Checklist

- admin can call `GET /api/v1/records/operations`
- response includes governed import and transfer counts
- response includes recent RM-governed import jobs
- response includes recent RM-governed transfer jobs
- trash restore now rejects declared records, file plans, nodes inside file-plan scope, and folders containing declared records
