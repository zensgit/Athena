# Phase369AI Archive Policy Backbone Verification

## Focused verification

Backend:

```bash
cd ecm-core && mvn -q -Dtest=ArchivePolicyServiceTest,ContentArchiveControllerTest,ContentArchiveServiceTest test
```

Expected coverage:

- dry-run collapses nested descendants and skips recent content
- execute archives eligible candidates and updates policy execution stats
- controller contract for upsert/dry-run/run endpoints
- archive service still supports archive/restore mutation semantics

Frontend:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/ContentArchivePage.tsx src/services/contentArchiveService.ts
cd ecm-frontend && npm run -s build
```

## Additional check

```bash
git diff --check
```

## Residual warnings

Frontend build still reports the same two pre-existing warnings outside this phase:

- [ShareLinkManager.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/share/ShareLinkManager.tsx)
- [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx)
