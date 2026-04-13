# P0A Ledger Fixup Verification

Date: 2026-04-13

## Scope Verified

This verification covers the fixup applied on top of the existing ledger work:

- runtime `DOCUMENT` owner maintenance
- orphan grace-period enforcement
- safer `073` backfill filters
- version-aware fallback reference checks

## Static Validation Completed

### Diff hygiene

- `git diff --check` passed
- constructor call sites for updated services were re-scanned:
  - `NodeService`
  - `CheckOutCheckInService`
  - `VersionService`

### Code-path validation completed

The following write paths were manually re-checked after patching:

- pipeline document persistence
- pipeline initial version creation
- version creation
- version promotion
- working-copy check-in
- node create/copy/permanent delete
- PDF-derived document creation

### Migration validation completed

`073-backfill-content-references.xml` now:

- filters working copies with `documents.is_deleted = false`
- excludes `versions.is_deleted = false`
- removes unsafe automatic rollback blocks

## Tests Added or Updated

### Updated

- `ContentReferenceServiceTest`
- `CheckOutCheckInServiceTest`
- `VersionServiceLockSemanticsTest`

### Added

- `MetadataPersistenceProcessorTest`
- `InitialVersionProcessorTest`
- `NodeServiceContentReferenceTest`

## Execution Status

### Automated test execution

Attempted command:

```bash
mvn -Dtest=ContentReferenceServiceTest,CheckOutCheckInServiceTest,VersionServiceLockSemanticsTest,NodeServiceContentReferenceTest,MetadataPersistenceProcessorTest,InitialVersionProcessorTest test
```

Result:

- not executable in this environment because `mvn` is not installed
- `ecm-core/` also does not include `./mvnw`

Because of that, this verification is currently:

- statically reviewed
- test-source updated
- not runtime-executed on this machine

## Recommended Next Validation

Run these in an environment with Maven available:

```bash
cd ecm-core
mvn -Dtest=ContentReferenceServiceTest,CheckOutCheckInServiceTest,VersionServiceLockSemanticsTest,NodeServiceContentReferenceTest,MetadataPersistenceProcessorTest,InitialVersionProcessorTest test
```

Then run one broader backend compile/test pass:

```bash
cd ecm-core
mvn test
```

## Residual Risks

1. `PR-3` is still required for true `check-in -> version chain` correctness.
2. Legacy upload still performs an avoidable double content write.
3. `AlfrescoContentService` remains adapter-grade and is not part of this fixup’s verified lifecycle.
