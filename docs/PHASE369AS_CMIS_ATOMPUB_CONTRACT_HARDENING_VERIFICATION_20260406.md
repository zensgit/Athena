# Phase 369AS: CMIS AtomPub Contract Hardening Verification

## Focus

Verify that AtomPub controller contracts are explicitly tested, stale read-only wording is removed, and AtomPub mutation/versioning flows converge on shared CMIS services.

## Verification

Run:

```bash
cd ecm-core && mvn -q -Dtest=CmisAtomPubControllerTest,CmisAtomPubSerializerTest,CmisContentVersioningServiceTest test
git diff --check
```

## Expected Outcome

- AtomPub service document and object/feed endpoints return Atom XML with the expected content types.
- AtomPub mutation endpoints return the expected HTTP status and map security/missing/IO failures correctly.
- AtomPub checkout/checkin/cancel-checkout reuse shared content/versioning semantics.
- No whitespace or patch formatting regressions remain.
