# P5 PR-76 RM Search Index Record Projection Foundation Verification

## Scope Verified

- browse/list node mapping now preserves RM declaration projection coming from list DTOs
- search results now expose additive RM declaration projection derived from indexed `rm:*` properties
- `FileList` and `SearchResults` both reuse the same `RecordStatusChip` projection helper
- no new backend API surface, migration, or evidence surface was added

## Checks

### Frontend targeted tests

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/utils/recordDeclarationUtils.test.ts src/services/nodeService.recordProjection.test.ts --forceExit
```

Result:

- `Test Suites: 2 passed, 2 total`
- `Tests: 5 passed, 5 total`

### Frontend production build

```bash
cd ecm-frontend && npm run build
```

Result:

- passed
- existing repo warnings remain:
  - `src/components/share/ShareLinkManager.tsx`
  - `src/pages/AdminDashboard.tsx`

### Backend targeted test

```bash
cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=SearchControllerTest
```

Result:

- `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`

### Static diff check

```bash
git diff --check
```

Result:

- passed

## Notes

- this slice intentionally stops at browse/search record-state visibility
- `AdvancedSearchPage` remains deferred for a later `P5` follow-up if operators need the same RM projection there
