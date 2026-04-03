# Phase369AH Archive Visibility Convergence Verification

## Focused verification

Backend:

```bash
cd ecm-core && mvn -q -Dtest=NodeServiceChildrenAclTest,FolderServiceContentsAclTest,ContentArchiveServiceTest,SearchAclFilteringTest,NodeDocumentArchiveProjectionTest,NodeDocumentPreviewProjectionTest,NodeDocumentLockProjectionTest test
```

Passed.

Notes:

- `NodeServiceChildrenAclTest` now verifies live child loading uses `ArchiveStatus.LIVE`
- `FolderServiceContentsAclTest` now verifies archived folders are hidden from live folder lookup
- `ContentArchiveServiceTest` now verifies archive/restore refresh the search index
- `SearchAclFilteringTest` now verifies full-text, advanced, and faceted search all add `archiveStatus=LIVE`
- `NodeDocumentArchiveProjectionTest` verifies the search projection includes archive status

Frontend:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/ContentArchivePage.tsx
cd ecm-frontend && npm run -s build
```

Passed.

## Additional checks

```bash
git diff --check
```

Passed.

## Residual warnings

`npm run -s build` still reports two pre-existing warnings outside this phase:

- [ShareLinkManager.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/share/ShareLinkManager.tsx)
- [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx)

They were not introduced by `369AH`.

## Outcome

Live browse/read/search now default-hide archived nodes, archive mutations keep the search index in sync, and archived rows are no longer routed back into the ordinary browse workspace.
