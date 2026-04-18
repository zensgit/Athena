# P4 PR-17 Record Declaration UI Verification

## Automated Verification

### Targeted front-end tests

Command:

```bash
cd ecm-frontend
CI=true npm test -- --watch=false --runInBand \
  src/services/recordsManagementService.test.ts \
  src/components/records/RecordStatusChip.test.tsx \
  src/components/records/DeclareRecordDialog.test.tsx
```

Result:

- `3` suites passed
- `6` tests passed
- `0` failures
- `0` errors

### Full front-end regression

Command:

```bash
cd ecm-frontend
CI=true npm test -- --watch=false --runInBand
```

Result:

- `66` suites passed
- `312` tests passed
- `0` failures
- `0` errors

### Production build

Command:

```bash
cd ecm-frontend
npm run build
```

Result:

- build succeeded
- remaining warnings are unrelated pre-existing unused imports in:
  - `src/components/share/ShareLinkManager.tsx`
  - `src/pages/AdminDashboard.tsx`

### Static diff check

Command:

```bash
git diff --check
```

Result:

- passed

## Manual Verification Targets

- admin opens document preview and sees `Declare Record` in the action menu for non-record documents
- admin can submit a declaration comment and get success feedback
- declared document shows record badge in preview toolbar
- declared document shows record declaration alert/details in preview body
- browse/file-list rows render a record badge whenever the incoming node payload already contains `rm:record`
