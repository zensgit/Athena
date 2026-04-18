# P4 PR-23 Archive Reopen Semantics Verification

## Implementation Summary

`PR-23` was implemented as a backend-only hardening slice.

Delivered behavior:

- `restore` remains the only backend archive reopen action
- `ContentArchiveService.restoreNode(...)` now computes the full archived scope before mutation
- restore now fails fast when the archived scope contains:
  - non-archived descendants
  - working copies
  - checked-out documents
  - declared records
  - file plans
  - file-plan-governed descendants
- RM enforcement now runs as a scope-aware preflight through `RecordsManagementService.assertRestoreScopeAllowed(...)`
- no frontend code changes were required because archive restore already lives only on the admin archive page

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/service/ContentArchiveService.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/test/java/com/ecm/core/service/ContentArchiveServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`

## Targeted Validation

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test -Dtest=ContentArchiveServiceTest,RecordsManagementServiceTest
```

Result:

- `Tests run: 30`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Coverage added by this slice includes:

- restore happy-path still succeeds for archived folder scope
- restore rejects archived scope containing non-archived descendants
- restore rejects archived scope containing working copies
- restore rejects RM-blocked descendants before any mutation occurs
- RM scope helper blocks archived folder scopes containing declared records
- RM scope helper blocks archived folder scopes containing file plan subtrees

## Full Regression

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test
```

Result:

- `Tests run: 1543`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 11`
- `BUILD SUCCESS`

## Static Checks

Command:

```bash
git diff --check
```

Result:

- passed

## Frontend Impact

No frontend code changes were needed for `PR-23`.

Rationale:

- archive restore remains exposed only through `ContentArchivePage`
- no API contract changed
- no new route or page action was introduced

Optional future work remains possible if product wants to relabel `Restore` to `Reopen` on the archive admin page, but that was intentionally kept out of this slice.
