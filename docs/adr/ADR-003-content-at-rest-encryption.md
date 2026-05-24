# ADR-003 Content-at-Rest Encryption

- Status: Deferred
- Date: 2026-05-24

## Context

ADR-001 §93 named "all tenant content is encrypted with the same key" as a consequence of the global shared storage model but did not resolve the question. The 2026-05-24 discovery (`docs/ADR_001_STORAGE_ROUTING_DISCOVERY_20260524.md` §6 / cross-cutting risk 6) confirmed the underlying gap in the live codebase:

1. **Content blobs are written plaintext on disk.** `ContentService.java:66-123` writes raw input bytes via Java NIO (`Files.newOutputStream`, `Files.move`, `Files.copy`). `grep -RIn 'Cipher\\|encrypt\\|SecretCrypto' ecm-core/src/main/java/com/ecm/core/service/ContentService.java` returns zero hits. There is no application-layer encryption wrapper in the content write path.
2. **Property encryption is metadata-only.** `NodePropertyEncryptionService.java:71-100` encrypts `node.properties` into `node.encryptedProperties` via `SecretCryptoService` (AES-GCM, per-key-version). The same service is **not invoked** by `ContentService` and was never intended to cover content blobs.
3. **KEK / key versions are global, not per-tenant.** `SecretCryptoService.java:44, 101` reads the active key version and the keys map from configuration with no tenant context. Even property encryption today is single-key across tenants.
4. **At-rest encryption is currently a storage-backend concern.** Operators relying on MinIO server-side encryption (or equivalent) get bucket-level / filesystem-level at-rest crypto, with a single shared key. No application-layer envelope encryption exists for content blobs.

Athena has no current incident or compliance requirement that demands application-layer envelope encryption of content. This ADR records the gap, names the options, and explicitly defers implementation.

## Decision Options

### Option A: Status quo — rely on storage-backend at-rest controls

Continue writing plaintext blobs through `ContentService`'s direct NIO path. At-rest encryption is delegated to the storage backend (filesystem-level encryption at the OS, MinIO SSE, dm-crypt, LUKS, EBS encryption, etc.). Athena does not own a content KEK and does not perform application-layer envelope encryption of blobs.

| Pros | Cons |
|------|------|
| Zero code change required. | Backend SSE typically uses one key per backend / bucket; no per-tenant cryptographic isolation. |
| Operationally simple — operators handle at-rest crypto outside Athena. | An operator misconfiguration (SSE off) leaks plaintext blobs to disk silently; Athena cannot detect this. |
| Aligns with current behavior; no migration cost. | Insider-threat / storage-admin scenarios are not mitigated by Athena. |
| Compatible with all downstream operational paths (backup, replication) unchanged. | ADR-001 §93's "shared encryption key" consequence remains live. |

### Option B: Application-layer envelope encryption, single key version

Encrypt blobs in `ContentService` before writing, using a `SecretCryptoService`-style envelope (AES-GCM, per-key-version). The same single key version covers all tenants — parallel to today's property encryption posture.

| Pros | Cons |
|------|------|
| At-rest plaintext gap is closed in the application layer regardless of backend SSE configuration. | Loses the ability to share a deduplicated blob across two tenants via plaintext SHA-256 lookup; either dedup is preserved with same-key-same-IV-same-plaintext (which leaks plaintext equality to anyone with key access) or dedup is sacrificed. |
| Reuses `SecretCryptoService` infrastructure already shipped for property encryption. | Adds storage-write CPU overhead per upload (AES-GCM stream cipher). |
| Per-key-version rotation enables in-place rewrap, mirroring property-encryption rewrap tooling. | Backup / restore must be aware of which key version each blob was encrypted under; ledger / metadata schema may need extension. |
| Does not require ADR-001 to resolve toward Option B or C first. | Still single-key across tenants — does not satisfy any "per-tenant cryptographic isolation" trigger from ADR-001. |

### Option C: Per-tenant envelope encryption

Each tenant has its own KEK (or its own data key derived from a tenant-scoped KEK). Blobs are encrypted before write under the tenant's key.

| Pros | Cons |
|------|------|
| Per-tenant cryptographic isolation at the content layer. | **Blocked** on ADR-001 resolving to Option B or C (per-tenant routing). Today's global `ContentService` has no per-tenant scope to drive key selection from. |
| Resolves both ADR-001 §93 and the discovery's risk 7 in one architecture move. | Adds per-tenant key lifecycle (provisioning, rotation, recovery, destruction-on-tenant-delete). Athena currently has no per-tenant key derivation. |
| Aligns with cryptographic data-residency mental models used by compliance reviewers. | Requires migration: re-encrypt existing blobs under the new per-tenant key when introduced. Existing dedup is invalidated; cross-tenant dedup is impossible by construction. |

## Decision

**Deferred.** No implementation is in flight. The status quo (Option A — rely on storage-backend at-rest controls) is the live operating mode. This ADR is opened to surface the question in writing, not to commit to a fix.

The decision should be re-evaluated when at least one of the Reopen Criteria below is met. Until then, operators are responsible for ensuring the underlying storage backend provides at-rest encryption appropriate to their deployment.

## Consequences

1. **Content blobs continue to land on disk in plaintext as far as Athena's application layer is concerned.** Any at-rest encryption is provided by the storage backend / filesystem / volume layer, outside Athena's control or detection.
2. **A single misconfigured SSE setting on MinIO or a missing filesystem-level encryption mount can leak plaintext blobs to anyone with disk access.** Athena cannot warn about this; the codebase has no probe for "is the backing storage encrypted".
3. **Per-tenant cryptographic isolation is not available** even for tenants that pay for it, until either (a) Option C ships (blocked on ADR-001) or (b) backend-side per-tenant SSE keys are configured outside Athena (Option C feel-alike at the infrastructure layer).
4. **No migration is required for the current state.** If Option B or C is later chosen, the migration cost is named in the Options table above; it is real and is the principal reason ADR-003 stays Deferred.
5. **ADR-001 §93's "shared encryption key" consequence remains live and is now explicitly tracked here rather than as an unresolved consequence inside ADR-001.**

## Reopen Criteria

This ADR should be reopened (and potentially promoted to Accepted under Option B or C) if any of the following observable conditions is met:

1. **Security audit / pentest finding** — A written security review identifies content-blob plaintext at rest as a real-world exfiltration risk (storage-admin insider threat, mistaken volume snapshot leak, backup-tape compromise, etc.), and recommends application-layer envelope encryption.
2. **Compliance contract requiring application-layer at-rest encryption** — A signed customer contract or compliance audit (e.g., FedRAMP, HIPAA at certain scope, regulator-specific) requires the application to perform its own at-rest encryption independent of the storage backend.
3. **Incident** — A blob plaintext leak occurs and the postmortem identifies application-layer envelope encryption as the prevention.
4. **ADR-001 resolves toward Option B or C** — Per-tenant routing infrastructure makes Option C newly viable. Even then, ADR-003 only auto-promotes if a separate trigger (1-3 above) justifies the per-tenant cryptographic work; routing alone is not sufficient.

## Out of Scope

This ADR does NOT propose, nor commit to:

- Modifying `ContentService`, `ContentReferenceService`, or any storage I/O path.
- Modifying `SecretCryptoService`, `NodePropertyEncryptionService`, or property encryption infrastructure.
- Introducing a content KEK, content key versions, or content rewrap tooling.
- Adding per-tenant key derivation, per-tenant key rotation, or per-tenant key destruction-on-tenant-delete.
- Adding probes that test whether the backing storage is encrypted (no agreed cross-backend probe exists).
- Modifying backup / restore tooling, transfer-replication protocol, or `application*.yml` / `docker-compose*` / Liquibase changelogs.
- Recommending operators turn off backend SSE; Option A explicitly relies on it.

## Next Steps

1. No action required until a Reopen Criterion is met.
2. If a Reopen Criterion fires, the first follow-up slice is a focused discovery doc (mirroring `ADR_001_STORAGE_ROUTING_DISCOVERY_20260524.md`) scoping the chosen Option's migration, key-lifecycle, and dedup impact.
3. Any future implementation must coordinate with ADR-001 (especially if Option C is chosen, which is gated on ADR-001 routing) and with ADR-002 (quota accounting, which would need to handle ciphertext-size vs plaintext-size accounting if Option B or C ships).
