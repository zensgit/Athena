# P3 PR-11A Secret Crypto Foundation Design

## Date
- 2026-04-14

## Status
- implemented

## Goal
- Introduce Athena's first reusable secret-protection abstraction without widening the scope to dynamic model-backed properties yet.
- Encrypt existing secret-bearing mail fields transparently at persistence time while keeping legacy plaintext rows readable.

## Why This Slice First
- Athena already has a real secret-bearing integration surface in Mail Automation.
- The lowest-risk foundation is:
  - one shared secret codec
  - one transparent persistence hook
  - one production code path using it
- This creates a reusable base for `PR-12 Generic OAuth Credential Store` without forcing a schema rewrite or a risky dynamic-property refactor in the same turn.

## Scope
- Added shared secret crypto foundation in:
  - `ecm-core/src/main/java/com/ecm/core/security/secret/SecretCryptoProperties.java`
  - `ecm-core/src/main/java/com/ecm/core/security/secret/SecretCryptoService.java`
  - `ecm-core/src/main/java/com/ecm/core/security/secret/EncryptedSecretConverter.java`
- Applied transparent encryption to `MailAccount` sensitive fields:
  - `password`
  - `oauthClientSecret`
  - `oauthAccessToken`
  - `oauthRefreshToken`
- Added configuration surface in `application.yml`
- Added persistence and unit coverage

## Explicit Non-Goals
- No encryption of dynamic `Node.properties` in this slice
- No generic OAuth credential aggregate yet
- No key rotation workflow API yet
- No Liquibase migration or backfill job

## Design

### Storage Format
- Encrypted values are stored inline in the existing columns using:
  - `enc:<keyVersion>:<base64Payload>`
- Payload is:
  - IV length byte
  - IV bytes
  - AES-GCM ciphertext bytes

### Algorithm
- `AES/GCM/NoPadding`
- random 12-byte IV
- 128-bit authentication tag

### Key Model
- Config-driven keys under:
  - `ecm.security.secret.enabled`
  - `ecm.security.secret.active-key-version`
  - `ecm.security.secret.keys`
- The stored prefix carries key-version metadata so future rotation can be introduced without changing column shape.

### Compatibility Strategy
- Legacy plaintext values remain readable.
- When encryption is enabled:
  - existing plaintext rows decrypt as plain strings because they are not prefixed
  - the next write of that entity persists the value in encrypted form
- This avoids a blocking migration before the foundation can ship.

### Persistence Hook
- `EncryptedSecretConverter` is attached via `@Convert` on `MailAccount` fields.
- Business services and controllers continue to read/write plain strings on the entity.
- Encryption stays below the service layer, reducing churn and reducing the chance of missed call sites.

## Configuration
- Added in `application.yml`:
  - `ecm.security.secret.enabled`
  - `ecm.security.secret.active-key-version`
  - `ecm.security.secret.keys.v1`

## Risks And Mitigations
- Risk: rollout with no key configured
  - Mitigation: encryption is disabled by default
- Risk: existing plaintext rows break reads
  - Mitigation: non-prefixed values are treated as legacy plaintext
- Risk: over-claiming scope
  - Mitigation: `PR-11A` is documented as foundation only; model-backed property encryption remains deferred

## Follow-On
- `PR-11B`: adopt the same abstraction for model-backed sensitive properties where policy allows
- `PR-12`: refactor mail OAuth to consume a generic OAuth credential store built on this foundation
