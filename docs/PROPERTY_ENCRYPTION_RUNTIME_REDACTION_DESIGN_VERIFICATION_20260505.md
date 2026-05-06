# Property Encryption Runtime Redaction Design Verification

Date: 2026-05-05

## Context

Property Encryption already stores model-declared encrypted properties in `nodes.encrypted_properties`, keeps them out of search indexing, and exposes aggregate-only admin operations for dry-run, backfill, rewrap, and execution ledgers.

The remaining runtime risk was defense-in-depth handling for abnormal or legacy values where a protected payload string such as `enc:v1:...` appears in a generic property map or a search highlight. Those values should never be displayed, indexed, or written back as ordinary user-editable property data.

This slice closes the raw protected-payload leak path. It does not change the existing product semantics that model-declared encrypted properties are decrypted by `resolveReadableProperties(...)` for trusted readable projections. A stricter default-masked generic API mode remains a product decision.

## Design

### Backend Redaction

`NodePropertyEncryptionService` now has a shared redaction marker:

```text
[encrypted]
```

The service recursively redacts protected payload-looking strings in plain property maps before returning readable or indexable maps:

- `resolveReadableProperties(...)` starts from `redactProtectedPayloads(node.getProperties())`
- `resolveIndexableProperties(...)` starts from the same redacted copy
- nested maps and collections are redacted recursively
- model-declared encrypted keys still override readable projections through explicit `encryptedProperties` reveal behavior

This protects against stale, malformed, imported, or otherwise abnormal data where a protected payload is present in `nodes.properties` instead of `nodes.encrypted_properties`.

### Frontend Redaction

Added `propertyRedactionUtils`:

- `isProtectedPropertyPayload(value)`
- `containsProtectedPropertyPayload(value)`
- `redactProtectedPropertyValue(value)`
- `redactProtectedPropertyText(value)`
- `formatPropertyDisplayValue(value)`

The utility is applied to:

- `PropertiesDialog`: property values render as `[encrypted]` instead of raw `enc:...`
- `PropertiesDialog`: redacted custom properties cannot be edited or deleted from the generic editor
- `PropertiesDialog`: save payload skips `[encrypted]` fields so a masked value is not written back as literal property data
- `SearchDialog`: prefilled property filters omit protected payload values
- `SearchResults`: highlight snippets and highlight summaries redact inline protected payload text before rendering

### Explicit Non-Goal

This slice intentionally does not switch generic `NodeDto.properties` from decrypted encrypted-property values to `[encrypted]`.

Current documented semantics from `docs/P3_PR11B_MODEL_PROPERTY_ENCRYPTION_DESIGN_20260414.md` are:

- encrypted model properties are removed from plaintext storage
- encrypted model properties are excluded from search indexing
- readable API projections can still reveal values through `resolveReadableProperties(...)`

If the benchmark requires default runtime masking for model-declared encrypted properties, the next slice should add a separate response policy such as `resolveResponseProperties(...)` and switch public DTO mappers to it while preserving explicit internal/trusted readable paths.

## Verification

### Backend Unit Test

Command:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=NodePropertyEncryptionServiceTest \
  test
```

Result:

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Coverage added:

- readable properties redact legacy plain `enc:...` payloads
- redaction recurses through nested maps
- redaction recurses through lists
- indexable properties redact legacy plain `enc:...` payloads
- existing readable projection for model-declared encrypted properties remains covered

### Frontend Unit Test

Command:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/utils/propertyRedactionUtils.test.ts \
  --watchAll=false
```

Result:

```text
Test Suites: 1 passed, 1 total
Tests: 5 passed, 5 total
```

Coverage added:

- protected payload detection avoids ordinary `enc` text
- recursive object/list redaction
- inline highlight text redaction
- formatted display values never expose raw protected payloads

### Frontend Lint

Command:

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

### Docker Gate

Command:

```bash
docker info --format '{{.ServerVersion}}'
```

Result:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

The Docker-backed PostgreSQL gate remains an environment blocker on this host, not a product regression.

## Remaining Work

Remaining Property Encryption closeout work:

- Run the Docker-backed PostgreSQL backfill/rewrap gate on a Docker-capable host.
- Decide whether model-declared encrypted properties should be readable by generic API projections or default-masked as `[encrypted]`.
- If default masking is required, add `resolveResponseProperties(...)` and switch public DTO mappers to that policy while preserving explicit trusted/internal readable paths.
- Record a final acceptance matrix after Docker-backed evidence is available.
