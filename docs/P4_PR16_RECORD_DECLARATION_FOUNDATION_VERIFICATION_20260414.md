# P4 PR-16 Record Declaration Foundation Verification

## Scope Verified

- RM model seed migration included as `080`
- dedicated admin record declaration API
- declaration metadata persisted on node aspect/properties
- mutation guardrails across node, trash, checkout, and version seams

## Targeted Tests

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test \
  -Dtest=RecordsManagementServiceTest,RecordsManagementControllerTest,RecordsManagementControllerSecurityTest,NodeServiceRecordDeclarationTest,TrashServiceRecordDeclarationTest,VersionServiceRecordDeclarationTest,CheckOutCheckInServiceRecordDeclarationTest
```

Result:

- `Tests run: 15`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Covered assertions:

- declaration writes `rm:record` + declaration metadata
- admin-only controller access
- declared documents cannot be checked out
- declared documents cannot have versions deleted
- folders containing declared records cannot be moved/deleted via user paths
- trash purge skips declared records

## Full Backend Regression

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test
```

Result:

- `Tests run: 1502`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 11`

## Static Check

Command:

```bash
git diff --check
```

Result:

- passed

## Residual Risk

- `080` was covered through compile/test packaging, but not through a staging non-empty-database Liquibase smoke in this environment.
- Generic aspect endpoints now reject direct `rm:record` adds, but no frontend authoring path exists yet; declaration remains backend/admin only by design.
- Governance destroy remains allowed for declared records so disposition can still execute. If future RM policy wants stricter destroy preconditions, that belongs in the next RM slice rather than this foundation PR.

