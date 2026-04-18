# P3 PR-14 Legal Hold Foundation Verification

## Date
- 2026-04-14

## Result
- `PR-14 approve`

## Targeted Backend Verification

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test \
  -Dtest=LegalHoldServiceTest,TrashServiceLegalHoldTest,NodeServiceLegalHoldTest,FolderServiceLegalHoldTest,VersionServiceLegalHoldTest,LegalHoldControllerTest,LegalHoldControllerSecurityTest
```

### Outcome
- `BUILD SUCCESS`
- `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`

## Full Backend Regression

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test
```

### Outcome
- `BUILD SUCCESS`
- `Tests run: 1470, Failures: 0, Errors: 0, Skipped: 11`

## Verified Behaviors
- Admin-only legal hold endpoints require authentication and `ROLE_ADMIN`.
- Legal holds persist as first-class aggregates and can be released with audit metadata.
- Held folders block descendant destructive operations through subtree-aware path matching.
- Held content cannot be moved, deleted, trashed, permanently deleted, or version-pruned through the guarded repository paths.
- `emptyTrash()` aborts before partial deletion when a held root item is present.
- `purgeOldTrashItems()` skips held items instead of deleting them.

## Deferred Coverage
- Archive / transfer / replication / bulk-import overwrite paths are not covered by `PR-14`.
- Frontend hold authoring is not part of this slice.
- Disposition integration is deferred to `PR-15`.

## Static Checks

```bash
git diff --check
```

### Outcome
- passed
