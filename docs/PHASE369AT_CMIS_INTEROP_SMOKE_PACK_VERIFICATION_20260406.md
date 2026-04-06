# Phase 369AT: CMIS Interop Smoke Pack Verification

## Verification

Run:

```bash
cd ecm-core && mvn -q -Dtest=CmisInteropSmokePackTest,CmisAtomPubControllerTest,CmisAtomPubSerializerTest,CmisBrowserControllerTest test
git diff --check
```

## Expected Outcome

- Browser binding query, mutation, and content selectors match the shared fixtures.
- AtomPub object and checkout responses match the shared XML fixtures.
- Browser and AtomPub smoke coverage remains pinned to the same public CMIS contract.
