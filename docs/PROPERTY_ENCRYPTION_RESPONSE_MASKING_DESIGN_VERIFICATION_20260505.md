# Property Encryption Response Masking Design Verification

Date: 2026-05-05

## Context

The previous runtime redaction slice prevented abnormal raw protected payload strings such as `enc:v1:...` from leaking through generic property maps, search prefill, search highlights, or frontend property editing.

One product decision remained: whether model-declared encrypted properties should continue to be decrypted in generic public API responses, or whether generic responses should mask them by default while preserving internal readable workflows.

This slice chooses the safer default: public/generic response projections mask model-declared encrypted properties as `[encrypted]`. Trusted internal workflows still use `resolveReadableProperties(...)` when they need plaintext for copy, checkout/checkin comparison, and compatibility mutations.

## Design

### Service Policy Split

`NodePropertyEncryptionService` now exposes two separate runtime read policies:

- `resolveReadableProperties(node)`: trusted/internal readable projection. It decrypts model-declared encrypted properties from `nodes.encrypted_properties` and still redacts abnormal raw protected payload strings from plain `nodes.properties`.
- `resolveResponseProperties(node)`: public/generic response projection. It never decrypts encrypted payloads. It redacts abnormal raw protected payload strings and replaces model-declared encrypted property keys with `[encrypted]`.

The masking policy covers both storage shapes:

- normal encrypted storage: key exists in `nodes.encrypted_properties`
- legacy/backfill-pending storage: encrypted model key still exists in plain `nodes.properties`

### Public Response Mappers

Switched the public/generic response mappers to `resolveResponseProperties(...)`:

- `NodeController.toNodeDto(...)`
- `DocumentController.toNodeDto(...)`
- `ContentTypeController.toNodeDto(...)`
- `AlfrescoNodeService.getProperties(...)`

These are the paths that return node properties to clients through generic DTO/property APIs.

### Internal Readable Paths Preserved

The following paths intentionally continue to use readable/plaintext projection because they need property values for internal mutation semantics:

- `NodeService` copy/update/aspect helper paths
- `CheckOutCheckInService` working-copy creation, metadata comparison, and checkin state transfer
- `NodePropertyEncryptionService.materializeReadableProperties(...)`

Search indexing continues to use `resolveIndexableProperties(...)`, not response masking.

### Search Index Hardening

While auditing response mappers, two subtree/child reindex paths were found to build `NodeDocument.fromNode(...)` without overriding `properties`. They now call `resolveIndexableProperties(...)` before saving to Elasticsearch:

- `SearchIndexService.updateNodeChildren(...)`
- `SearchIndexService.reindexNodeSubtree(...)`

This keeps encrypted model keys and abnormal protected payloads out of stored search documents.

## Verification

### Backend Targeted Test Suite

Command:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=NodePropertyEncryptionServiceTest,NodeControllerAspectTest,DocumentControllerCheckoutTest,ContentTypeControllerPreviewSemanticsTest,SearchIndexServiceSubtreeReindexTest \
  test
```

Result:

```text
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Coverage added or extended:

- `resolveResponseProperties(...)` masks encrypted model properties without calling crypto reveal.
- `resolveResponseProperties(...)` masks legacy plaintext values for encrypted model keys.
- `NodeController` response DTO uses masked property projection.
- `DocumentController` checkout response DTO uses masked property projection.
- `ContentTypeController` apply-type response DTO uses masked property projection.
- `SearchIndexService.reindexNodeSubtree(...)` stores indexable property projection, not raw node properties.

### Docker Gate

The Docker-backed PostgreSQL gate remains environment-blocked on this host because the Docker socket is unavailable:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

This is still a host capability blocker, not a product regression.

## Remaining Work

Property Encryption closeout now has only environment/final-evidence work left:

- Run Docker-backed PostgreSQL backfill/rewrap verification on CI or a Docker-capable host.
- Record the first Docker-backed green run in the closeout evidence.
- Produce the final acceptance matrix once Docker-backed evidence is available.

No known code slice remains for runtime protected-payload redaction or default response masking.
