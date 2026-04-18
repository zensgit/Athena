# P4 PR-19 RM Governance Hardening Verification

## Scope Verified

- manual archive/restore reject RM-governed content
- archive policy rejects file-plan folders
- archive policy candidate selection excludes declared records and file-plan-governed nodes
- archive policy archive path now honors legal holds
- folder copy now prechecks legal holds
- RM summary and audit endpoints are exposed and serializable
- RM audit events are written on record declaration/category/file-plan mutations

## Targeted Tests

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test \
  -Dtest=NodeServiceLegalHoldTest,RecordsManagementServiceTest,RecordsManagementControllerTest,ContentArchiveServiceTest,ArchivePolicyServiceTest,ArchivePolicyServiceDispositionConflictTest
```

Result:

- `Tests run: 34`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Covered assertions:

- `copyNode(...)` rejects held sources before recursive copy
- manual archive rejects RM-governed content
- policy archive rejects held content
- archive policy rejects file-plan folders
- archive policy dry-run skips RM-governed candidates
- RM summary endpoint returns aggregate counts
- RM audit endpoint returns paged `RM_%` timeline payload

## Full Backend Regression

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test
```

Result:

- `Tests run: 1522`
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

- This slice audits bulk-import and transfer overwrite seams but does not add new transfer/import-specific RM telemetry.
- There was no staging non-empty-database exercise in this environment, though `PR-19` itself does not add a migration.
- RM reporting is API-only in this slice; admin dashboards remain deferred to the next UI-oriented PR.
