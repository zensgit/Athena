# Phase369AO CMIS Basic Mutation Backbone Verification

## Backend

Focused tests:

```bash
cd ecm-core && mvn -q -Dtest=CmisMutationServiceTest,CmisBrowserControllerTest test
```

Expected coverage:

- create folder against virtual root
- create document skeleton plus secondary metadata/property updates
- property mapping for `cmis:name`, `cmis:description`, `athena:metadata.*`, `athena:property.*`
- blank `cmis:name` rejection
- soft-delete dispatch
- controller POST action contract
- controller unsupported-action rejection
- controller permission-error mapping

## Diff hygiene

```bash
git diff --check
```

## Manual sanity checks

1. `POST /api/v1/cmis/browser?cmisaction=createFolder` with `folderId=root` and a JSON body containing `name`.
2. `POST /api/v1/cmis/browser?cmisaction=createDocument` with `folderPath` or `folderId`, and confirm a document skeleton is created.
3. `POST /api/v1/cmis/browser?cmisaction=updateProperties` with `cmis:name` and `athena:metadata.*` fields.
4. `POST /api/v1/cmis/browser?cmisaction=deleteObject` and confirm the node is soft-deleted.
5. Confirm unsupported `cmisaction` returns `400`.
