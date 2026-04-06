# Phase 369AN: CMIS Query/Discovery Backbone Verification

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

- `cmisselector=query` returns read-only CMIS query results
- supported statements include:
  - `SELECT * FROM cmis:document`
  - `SELECT * FROM cmis:folder`
  - `WHERE IN_FOLDER(...)`
  - `WHERE cmis:name = ...`
  - `WHERE cmis:name LIKE ...`
- unsupported selectors still return `400 BAD_REQUEST`
- query results are mapped back into CMIS object payloads
