# P3 PR-11B Model Property Encryption Design

## Date
- 2026-04-14

## Status
- implemented

## Goal
- Extend the shared secret crypto foundation from `PR-11A` into model-backed node properties.
- Keep encrypted model properties out of plaintext node storage and search indexing while preserving readable API projections.

## Scope
- Added Liquibase `079-add-node-encrypted-properties.xml`
  - `property_definitions.encrypted boolean not null default false`
  - `nodes.encrypted_properties jsonb`
- Added model metadata support:
  - `PropertyDefinition.encrypted`
  - `PropertyDefinitionDto.encrypted`
- Added node storage support:
  - `Node.encryptedProperties`
  - `NodeRepository.countByPropertyKeyAcrossStorageAndDeletedFalse(...)`
- Added `NodePropertyEncryptionService`
  - resolve encrypted property keys from dictionary metadata
  - move encrypted values from `properties` to `encryptedProperties` before persistence
  - decrypt back into readable property maps for API/view consumers
  - strip encrypted keys from indexable/search-bound property maps
- Wired encryption preparation into the main write seams:
  - `NodeService`
  - `MetadataPersistenceProcessor`
  - `CheckOutCheckInService`
- Wired readable projection into:
  - `NodeDto`
  - `NodeController`
  - `DocumentController`
  - `ContentTypeController`
  - `AlfrescoNodeService`
- Wired index sanitization into:
  - `SearchIndexService`
  - `FullTextSearchService`
- Added validation and authoring support:
  - `RuntimeModelValidationService`
  - `ContentModelService`
  - `ecm-frontend/src/pages/ContentModelsPage.tsx`
  - `ecm-frontend/src/services/contentModelService.ts`

## Explicit Non-Goals
- No backfill of existing plaintext node property values into `nodes.encrypted_properties`
- No masking UX yet in runtime property editors such as `PropertiesDialog`
- No per-property key selection or dedicated key-rotation workflow for model properties

## Design

### Storage Model
- Plain model properties continue to live in `nodes.properties`.
- Encrypted model properties are persisted in `nodes.encrypted_properties`.
- The encrypted payload uses the `PR-11A` `SecretCryptoService`, so stored values retain key-version metadata.

### Dictionary-Driven Policy
- Encryption is controlled by `PropertyDefinition.encrypted`.
- `NodePropertyEncryptionService` resolves encrypted keys from:
  - the node type definition
  - attached aspect definitions
- This keeps policy in the content model rather than scattered through call sites.

### Write Path
1. Business code writes readable values into `node.properties`.
2. Before save, `NodePropertyEncryptionService.prepareForPersistence(...)`:
   - identifies encrypted model keys
   - encrypts their values
   - removes them from plaintext `properties`
   - stores encrypted payloads in `encryptedProperties`
3. The entity is then persisted without cleartext leakage for encrypted keys.

### Read Path
- API/controller surfaces should continue to expose readable values.
- Instead of mutating every loaded entity globally, controllers and compatibility services project readable properties on demand through `NodePropertyEncryptionService.resolveReadableProperties(...)`.
- This avoids accidentally re-flushing decrypted state.

### Search And Indexing
- Search indexing uses sanitized property maps from `resolveIndexableProperties(...)`.
- Encrypted property keys are removed before Elasticsearch documents are built or refreshed.
- This keeps search semantics aligned with the governance rule that encrypted properties are never indexed.

### Model Validation
- `RuntimeModelValidationService` now rejects encrypted properties that would also be indexed.
- Encrypted property definitions also require secret crypto to be enabled.
- `ContentModelService.addProperty(...)` normalizes encrypted properties to `indexed=false` before validation and persistence so UI/API authoring remains usable.

### Frontend Authoring
- `ContentModelsPage` now exposes an `Encrypted` toggle in the add-property dialog.
- The property table shows an `Encrypted` chip for dictionary properties.
- `contentModelService` threads `encrypted` through both type and aspect property authoring APIs.

## Compatibility Strategy
- Existing models default to `encrypted=false`.
- Existing plaintext nodes remain readable because non-encrypted properties stay in `nodes.properties`.
- Encrypted model properties require:
  - `ecm.security.secret.enabled=true`
  - an active key version
  - configured keys
- If encrypted model properties are defined while secret crypto is disabled, writes fail fast instead of silently storing plaintext.

## Risks And Mitigations
- Risk: encrypted property definitions become impossible to create because `PropertyDefinition.indexed` defaulted to `true`
  - Mitigation: `ContentModelService.addProperty(...)` now coerces encrypted properties to `indexed=false`
- Risk: decrypted values leak into search documents
  - Mitigation: indexing paths now use sanitized property maps
- Risk: controllers re-emit ciphertext or drop encrypted values from API reads
  - Mitigation: readable projection is handled centrally via `NodePropertyEncryptionService`
- Risk: model deletion validation misses encrypted-property usage
  - Mitigation: property usage checks now inspect both `properties` and `encrypted_properties`

## Follow-On
- Add masking/redaction behavior to runtime property editing surfaces if operators should not see decrypted values by default
- Add migration/backfill tooling if existing plaintext node properties need to be moved into encrypted storage
- Extend the same policy model to additional dynamic metadata surfaces only after the node-property path is stable
