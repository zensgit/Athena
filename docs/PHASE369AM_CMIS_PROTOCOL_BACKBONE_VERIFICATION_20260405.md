# Phase 369AM: CMIS Protocol Backbone Verification

## Focused Verification

Backend:

```bash
cd ecm-core && mvn -q -Dtest=CmisBrowserServiceTest,CmisBrowserControllerTest test
```

Diff hygiene:

```bash
git diff --check
```

## Expected Outcomes

- `repositoryInfo` returns Athena repository metadata
- `typeChildren` returns at least `cmis:folder` and `cmis:document`
- `object` resolves both the virtual root and real Athena nodes
- `children` returns root folders from the virtual root and folder contents for real folders
- unsupported selectors fail with `400 BAD_REQUEST`
