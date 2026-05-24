# ADR-001 Storage Routing / Tenant Isolation — Read-Only Discovery

Date: 2026-05-24

## Headline

ADR-001 was authored 2026-04-11 with status **Deferred** and a deliberate choice to retain the global shared storage model (Option A). The codebase as of HEAD `5d97950` matches that decision verbatim — no per-tenant storage routing has been added since.

The discovery returns three actionable findings:

1. **The "Deferred" status no longer accurately describes reality.** Option A is the live implementation; calling it deferred makes the decision look pending when it is shipped. Honest update: status `Accepted: Option A` with codified exit triggers.
2. **ADR-001 §93 named content-blob plaintext at-rest as a consequence but did not resolve it.** Property encryption shipped 2026-05-05 ~ 2026-05-12 covers node properties (metadata), not blobs. This is a separable concern that warrants its own ADR (proposed: ADR-003 content-at-rest encryption), not an update to ADR-001.
3. **ADR-002 §12 / §47 already identified the dedup-quota asymmetry as future work.** The discovery confirms the asymmetry is still live (`ContentService.java:83-89` returns before quota check on dedup hit) and is owned by ADR-002, not ADR-001. Not in scope for ADR-001 resolution.

No trigger condition listed in ADR-001 §80-88 has materialized between 2026-04-11 and 2026-05-24. The discovery does not recommend implementing Option B or C. The discovery does recommend updating ADR-001's status from Deferred → Accepted (Option A) and opening ADR-003 for the orthogonal content-blob encryption concern.

## Method

Read-only scan. Primary sources directly read:

- `CLAUDE.md` Frozen Architecture / Do NOT / Roadmap sections.
- `docs/adr/ADR-001-storage-routing-tenant-isolation.md` (96 lines, full read).
- `docs/adr/ADR-002-tenant-quota-accounting-and-context-boundaries.md` (48 lines, full read).
- `ecm-core/src/main/java/com/ecm/core/service/ContentService.java` (storage flow + dedup + quota call site).
- `ecm-core/src/main/resources/application.yml` (storage config).
- `ecm-core/src/main/resources/application-docker.yml` (Docker storage config + MinIO config).
- Explore agent fan-out for cross-file citations (entity / repository / migration / TenantWorkspaceScopeService / transfer / property encryption surfaces).

Two Explore-asserted high-impact claims independently grep-verified at primary source before inclusion: (a) `ContentService` performs no encryption (empty grep result for `Cipher` / `SecretCrypto` / `encrypt` in `ContentService.java`); (b) `ecm.storage.type` property exists but no switching code consumes it.

## 1. Current content storage architecture (Q1)

| Layer | File:line | What it does |
|---|---|---|
| Upload entry | `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java:134-158` | Receives `MultipartFile`, calls `contentService.storeContent(file)` and persists the returned `contentId` on the Document entity |
| Storage core | `ecm-core/src/main/java/com/ecm/core/service/ContentService.java:66-123` | `storeContent(InputStream, String)` — copy to temp file while computing SHA-256, dedup lookup, generate timestamp+UUID contentId, atomic move into `{rootPath}/{year}/{month}/{day}/{contentId}`, then authoritative quota check |
| Dedup lookup | `ContentService.java:299-303` | `findExistingContent(contentHash)` → `documentRepository.findByContentHash(contentHash)`; returns existing `contentId` if hit |
| Reference ledger | `ecm-core/src/main/java/com/ecm/core/service/ContentReferenceService.java` (entire file) | `content_references` table maintains the authoritative reference graph by `(content_id, owner_type, owner_id)`; owner types are DOCUMENT / VERSION / WORKING_COPY / RENDITION |
| Hash storage | `ecm-core/src/main/resources/db/changelog/changes/003-create-node-tables.xml:89` and `004-create-version-tables.xml:36` | `documents.content_hash VARCHAR(128)` and `versions.content_hash VARCHAR(128)`; **nullable** in current schema |
| Storage IO | `ContentService.java:75-105` | Direct Java NIO calls (`Files.newOutputStream`, `Files.move`, `Files.copy`); no adapter interface |

End-to-end flow: controller → ContentService → temp file write + SHA-256 compute → DocumentRepository hash lookup → either reuse existing contentId (dedup hit) or atomic-move to date-sharded path on local filesystem → TenantQuotaService post-write authoritative check.

**No `StorageAdapter` / `BlobStore` interface exists.** A future per-tenant routing decision would need to introduce this abstraction before any backend swap.

## 2. Current dedup mechanism (Q2)

| Aspect | File:line | Detail |
|---|---|---|
| Hash algorithm | `ContentService.java:286-297` | SHA-256 via Apache Commons Codec `DigestUtils.sha256Hex()` (`import org.apache.commons.codec.digest.DigestUtils` at `ContentService.java:10`); digest computed on-the-fly via a `DigestOutputStream`-shaped helper during temp-file write |
| Hash storage | `Document.java:77-78` and `Version.java:49-50` entity `@Column(name = "content_hash")` | Stored on both Document and Version entities; same column name |
| Hash lookup site | `DocumentRepository.findByContentHash(String)` (called at `ContentService.java:299-303`) | Plain repository method with no tenant scope predicate |
| Dedup enforcement | `ContentService.java:83-89` | Lookup-before-write: if hash hit, returns existing `contentId` and skips disk write entirely |
| Reference counting | `ContentReferenceService.java:30-194` | Authoritative ledger `content_references (content_id, owner_type, owner_id)` with unique constraint per migration `072-create-content-reference-ledger.xml:21-25` |
| Orphan cleanup | `ContentReferenceService.java:160-194` | Scheduled `cleanupOrphanedContent()` finds zero-ref content past grace period; **guarded `ecm.storage.orphan-cleanup.enabled: false` by default** (`ContentReferenceService.java:38`); CLAUDE.md flags this requires verification of backfill migration 073 before enabling |
| Backfill | `ecm-core/src/main/resources/db/changelog/changes/073-backfill-content-references.xml` | Three changesets seed ledger for DOCUMENT / WORKING_COPY / VERSION pre-existing rows |

**Dedup scope: global across tenants.** The hash lookup at `ContentService.java:299-303` calls `documentRepository.findByContentHash(...)` with no tenant predicate. ADR-001:19 explicitly documents this: *"deduplication operates globally across all tenants."*

## 3. Current tenant isolation surface (Q3)

| Layer | File:line | Scope |
|---|---|---|
| Tenant context | `ecm-core/src/main/java/com/ecm/core/config/TenantContext.java` | ThreadLocal `CURRENT_TENANT` (domain) + `CURRENT_TENANT_ROOT_NODE_ID` |
| Workspace scope service | `ecm-core/src/main/java/com/ecm/core/service/TenantWorkspaceScopeService.java:43-130` | Methods: `isNodeVisible`, `isPathVisible`, `isSiteVisible`, `isActivityVisible`. **All scope checks are path-prefix or node-id based**; none touch content blobs |
| Quota | `ecm-core/src/main/java/com/ecm/core/service/TenantQuotaService.java:29-75` | Per-tenant via `TenantContext.getCurrentTenantDomain()` → `DocumentRepository.sumFileSizeByPathPrefix(rootPath + "/%")` |

**Critical: `TenantWorkspaceScopeService` is NOT called from `ContentService` or `ContentReferenceService`.** A direct grep `grep -RIl "TenantWorkspaceScopeService" ecm-core/src/main/java/com/ecm/core/service/Content*` returns nothing. Content writes / lookups bypass the workspace scope entirely.

ADR-001:21 stated this in writing: *"Content storage itself has no awareness of tenants — all tenants share the same directory tree."*

Behavioral implication: Tenant A uploads a file with hash `h`; Tenant B later uploads bit-identical content; B's upload deduplicates against A's stored blob. Tenant B's Document points to the contentId Tenant A first created. The blob lives in a directory path that is not tenant-scoped. **No application-layer barrier prevents this.**

## 4. Current storage backend (Q4)

| Source | File:line | Evidence |
|---|---|---|
| Default backend declaration | `application.yml:158-161` | `ecm.storage.type: filesystem`, `root-path: /var/ecm/content`, `temp-path: /var/ecm/temp` |
| Docker overlay | `application-docker.yml:47-49` | `root-path` / `temp-path` env-overridable via `ECM_STORAGE_ROOT_PATH` / `ECM_STORAGE_TEMP_PATH` |
| MinIO config block | `application-docker.yml:69-71` | `minio.endpoint: ${MINIO_ENDPOINT:http://minio:9000}` — defined but **not consumed by ContentService** |
| Storage IO calls | `ContentService.java:75-105` | All Java NIO local filesystem (`Files.newOutputStream`, `Files.move`, `Files.copy`); no S3 client, no MinIO client, no adapter dispatch |
| `ecm.storage.type` switching code | (none — `grep -RIn 'ecm.storage.type' ecm-core/src/main/java` returns 0 hits) | The property is declared in YAML but no Java code reads it to switch implementations |

**ADR-001:25 stated "Production deployments use MinIO".** That sentence describes the operational target, not the codebase. The codebase as of `5d97950` exclusively writes to local filesystem. MinIO is presumed mounted on the local path (e.g., via FUSE) so the application sees it as a local directory tree. There is no S3 SDK on the classpath of `ecm-core` content path.

This is an important precision point: any Option B (per-tenant path) or Option C (per-tenant bucket) implementation would currently need to introduce the storage abstraction first; Option C in particular requires the S3 SDK as a new dependency.

## 5. ADR-001 unresolved blocker — what actually blocks resolution (Q5)

ADR-001 identifies three Options (A keep-as-is, B per-tenant path, C per-tenant bucket) and decides "Deferred". The discovery analyses **what would actually need to be resolved** to lift the deferral:

| Blocker | File:line evidence | Status |
|---|---|---|
| No `StorageAdapter` interface | `ContentService.java:66-123` (direct NIO calls only) | **Genuine blocker for Option C.** Option B (path interpolation in same FS) can be done in-place without an adapter; Option C needs the abstraction first |
| Dedup-vs-isolation tradeoff is unresolved | ADR-001:54 ("Loses cross-tenant deduplication") | **The core unresolved question.** No new evidence in the codebase to support choosing dedup-loss; equally no compliance signal to support choosing isolation |
| Migration story is absent | `scripts/` and `docs/` contain no content-migration tooling; only schema migrations in `db/changelog/changes/` | **Genuine blocker for B and C.** Existing content lives under flat date-sharded paths; moving it under per-tenant prefixes requires offline tooling that does not exist |
| Backup / restore story is undocumented | No `docs/*BACKUP*`, `*RESTORE*`, `*DISASTER*` file found | **Operational gap.** Even Option A's tenant-delete pain is unresolved; ADR-001:92 acknowledges scanning is required, no implementation exists |
| Per-tenant key isolation has no infrastructure | `SecretCryptoService.java:44, 101` — keys are per-version (v1, v2), not per-tenant | **Conditional blocker.** If "per-tenant encryption" trigger ever fires, neither key-derivation nor key-rotation infrastructure exists |

**Honest characterization:** ADR-001's "Deferred" is best understood as "Accepted Option A with deliberately unwritten exit playbook". The genuine blockers to switching are not architectural disagreement; they are absence of (a) storage abstraction layer, (b) migration tooling, (c) per-tenant key derivation, (d) backup/restore design.

None of these blockers has gained traction or evidence since 2026-04-11.

## 6. Option spectrum tradeoff (Q6)

ADR-001's three Options are restated below with refined tradeoff cells based on the discovery's new evidence. Two columns added vs ADR-001: **property-encryption impact** and **transfer-replication impact**.

### Option A: Global shared dedup (current, live)

| Dimension | Detail |
|---|---|
| Compliance value | None — no physical tenant isolation. Suitable for tenants who do not require data residency / physical isolation. |
| Storage cost | Lowest. Cross-tenant identical content stored once. |
| Migration cost | Zero — already implemented. |
| Code impact | Zero. Status is the live state. |
| Operational impact | Tenant deletion requires document-table scan to find content IDs (`ADR-001:92`). No per-tenant backup granularity without app-level filtering (`ADR-001:96`). |
| Property encryption impact | Property encryption (`NodePropertyEncryptionService`) encrypts node metadata only. Content blobs are plaintext at rest. Per-tenant KEK is not possible (`SecretCryptoService.java:44, 101` — shared key versions across tenants). |
| Transfer replication impact | Receiver re-uploads content as multipart (`TransferReceiverService.java:327`). Each side deduplicates locally. Identical content uploaded to two receivers stored twice. No change. |
| Dedup-quota interaction | Dedup-hit path skips post-write quota check (`ContentService.java:83-89`); preflight still runs but undercounts post-write reality. Already named as ADR-002 future work (`ADR-002:12, 47`). |

### Option B: Per-tenant path interpolation `/{rootPath}/{tenantDomain}/{...}/{contentId}`

| Dimension | Detail |
|---|---|
| Compliance value | Moderate — filesystem-level path separation. Tenant directories can be backed up / restored independently. Falls short of cryptographic isolation; an attacker with FS access still sees all tenants. |
| Storage cost | Increases. Loses cross-tenant dedup. Same file uploaded by N tenants stored N times. Magnitude depends on tenant overlap rate, which the codebase has no telemetry to measure. |
| Migration cost | Real but bounded. All existing content lives under flat date paths; a one-off offline migration moves each blob into `{tenantDomain}/{...}` based on owning Document's tenant. Roughly: enumerate documents → for each, derive tenant from `TenantWorkspaceScopeService` → move blob → update `Document.contentId` to new path-encoded id (or keep contentId schema but change resolution rule). Tooling does not exist. |
| Code impact | Introduce `StorageAdapter` interface (or modify `ContentService.getStoragePath()` directly to consult `TenantContext.getCurrentTenantDomain()`). Update dedup lookup to be tenant-scoped — `findByContentHashAndTenantDomain(...)`. Update `ContentReferenceService` ledger semantics if dedup scope changes. Estimate: 2-4 person-weeks for the routing + lookup + tests, excluding migration tooling. |
| Operational impact | Tenant deletion becomes `rm -rf {tenantDomain}` — much cleaner. Per-tenant backup is straightforward. Tradeoff: connection-pool / inode pressure if tenant count is high. |
| Property encryption impact | Enables per-tenant encryption via subtree-level encryption keys. But this is orthogonal to routing — no automatic gain without ADR-003-style separate decision on content-at-rest encryption. |
| Transfer replication impact | Receivers still re-upload as multipart, but each receiver's local dedup pool is now tenant-scoped. No protocol change required. |
| Dedup-quota interaction | Improves: dedup hit can only occur within a tenant, so the dedup-skip-quota gap collapses to a within-tenant accounting concern (still owned by ADR-002 but smaller blast radius). |

### Option C: Per-tenant MinIO buckets

| Dimension | Detail |
|---|---|
| Compliance value | Highest at architecture level. Bucket-level IAM, server-side encryption with bucket-specific keys, independent lifecycle rules (retention, replication). Bucket boundary aligns with most regulatory data-residency mental models. |
| Storage cost | Same loss of cross-tenant dedup as Option B. Adds per-bucket overhead (provisioning, connection pool, possibly fixed-cost minimum). |
| Migration cost | Highest. Requires (a) introducing S3 SDK / MinIO client on classpath, (b) per-tenant bucket provisioning, (c) moving all existing content from local FS into per-tenant buckets, (d) updating contentId resolution to include bucket reference. Estimate: 4-8 person-weeks, with significant operational coordination on MinIO cluster sizing. |
| Code impact | Mandatory `StorageAdapter` interface with at least two implementations (filesystem for dev, MinIO for production). Bucket lifecycle management (`onTenantProvisioned` / `onTenantDeleted` hooks). Connection management. Async replication semantics if MinIO is deployed across regions. |
| Operational impact | Each tenant has independent backup/restore via MinIO bucket replication. Bucket lifecycle policies per tenant. Failure modes: bucket-create latency, IAM policy drift, cross-region replication monitoring. |
| Property encryption impact | Native MinIO SSE per bucket with distinct keys. Removes Athena-side responsibility for at-rest encryption of blobs. Resolves ADR-001:93 ("Shared encryption key") concern directly. |
| Transfer replication impact | Transfer between Athena instances now operates against per-tenant buckets on each side. Receiver registration must include target bucket mapping. Significant protocol surface change. |
| Dedup-quota interaction | Same as Option B (within-tenant only). |

### Option spectrum recommendation

ADR-001:80-88 lists four trigger conditions. As of `5d97950`:

- (1) Regulatory / compliance — no signal.
- (2) Per-tenant encryption — no signal in product roadmap. ADR-001:93 names it as a consequence; no customer ask.
- (3) Tenant lifecycle at scale — no signal (tenant count and turnover are within "scan-the-document-table" tolerance).
- (4) Diminishing dedup returns — no telemetry exists to even measure this. Codebase has no metric for dedup hit rate or storage savings.

**The discovery does not surface a new trigger.** Option A remains the right live choice.

## 7. Migration story (Q7)

If Option B or C ever ships, the migration path looks like:

| Stage | Detail |
|---|---|
| Pre-cutover | Introduce `StorageAdapter` interface; ship Option A as the default implementation with no behavior change. Add per-tenant tenant-scoped dedup lookup behind a feature flag (off). |
| Dual-write | New uploads write to both old global path AND new per-tenant path. Reads still served from old path. Window: until backfill completes. |
| Backfill | Offline tool enumerates existing documents, derives tenant from `TenantWorkspaceScopeService.resolveOwningTenant(document)`, copies blob to new per-tenant path. Verify hash matches at destination before flipping read source. |
| Cutover | Feature flag flipped. Reads served from new per-tenant path. Old path retained as read-only fallback for safety. |
| Rollback | Feature flag flip-back; new path retained for next attempt. Old path is still authoritative throughout dual-write. |
| Cleanup | After verification period (weeks), drop old global path tree. |

**None of this tooling exists.** Migration would be its own ~2 person-week sub-project before the routing change can land.

## 8. Downstream unlocks once ADR-001 is resolved (Q8)

If Option B or C ever ships, the following tracks become viable:

| Unlock | What it enables | Currently blocked by |
|---|---|---|
| Per-tenant KEK | Encrypt each tenant's content tree with a distinct key. Resolves ADR-001:93 + the property-encryption-doesn't-cover-blobs gap surfaced in this discovery. | No per-tenant subtree exists. |
| Data residency | Pin a tenant's blobs to a specific region / cluster / bucket. Required for many EU / government use cases. | No per-tenant routing exists. |
| Per-tenant backup / restore | Restore a single tenant from a backup without touching others. Currently requires app-level filtering. | No per-tenant subtree exists. |
| Per-tenant lifecycle policy | Retention, replication, archive tier — independent per tenant. | No per-tenant subtree exists. |
| Per-tenant transfer-replication target | Replicate a tenant to a tenant-specific receiver, possibly in a different region. | Transfer current model assumes shared receiver-side dedup pool. |
| Quota by physical blob footprint | ADR-002 names physical-vs-logical quota accounting as open. Per-tenant routing makes physical-footprint quota straightforward (`du -s {tenant}`). | Global pool makes physical accounting require ledger walks. |

This list intentionally cites which unlocks are blocked by Option A. None is currently a customer ask; each becomes available only if/when ADR-001 is resolved toward B or C.

## 9. Out of scope (explicit)

This discovery does NOT:

- Modify `ContentService`, `ContentReferenceService`, or any storage I/O code.
- Modify the SHA-256 hash algorithm, hash storage column, or dedup lookup.
- Add or remove storage backends. The `ecm.storage.type` property remains declared-but-unconsumed.
- Modify ACL paths, `TenantWorkspaceScopeService`, or `TenantContext` propagation.
- Modify property encryption (`SecretCryptoService`, `NodePropertyEncryptionService`) — the content-at-rest gap is recorded for a separate ADR-003 proposal, not resolved here.
- Modify transfer / replication code.
- Modify backup / restore tooling (none exists; that absence is noted, not addressed).
- Touch `.env`, `application*.yml`, `application*.properties`, `docker-compose*`, or Liquibase changelog files.
- Add a `StorageAdapter` interface.

## Cross-cutting risks observed (not all owned by ADR-001)

Surfaced during discovery; not all resolvable by ADR-001's scope:

1. **`Document.content_hash` is nullable** (`db/changelog/changes/003-create-node-tables.xml:89`). Migration 073 backfills the ledger but does not enforce NOT NULL on `content_hash`. Pre-existing legacy rows may still have null hash, breaking dedup invariant on those rows. Owner: future cleanup, not this ADR.
2. **`ecm.storage.orphan-cleanup.enabled` defaults to false** (`ContentReferenceService.java:38`). Without it, orphaned blobs accumulate indefinitely. Operationally a footgun; flag must be enabled only after migration 073 is verified (CLAUDE.md note). Owner: ops doc, not ADR-001.
3. **Dedup-quota asymmetry** (`ContentService.java:83-89` returns before quota check on dedup hit) — already named in ADR-002 §12 / §47. Owner: ADR-002 follow-on, not ADR-001.
4. **No backup/restore design doc exists**. ADR-001:89-96 names the consequence without proposing tooling. Discovery confirms no `docs/*BACKUP*` artefact. Owner: separate ops doc.
5. **`ecm.storage.type: filesystem` property declared but no Java switching code**. If a future contributor reads the YAML and assumes pluggable storage, they will be surprised. Owner: small doc cleanup or property removal.
6. **Content blob plaintext at rest** (`ContentService.java:75-105` writes raw bytes via Java NIO; `grep -E 'Cipher|encrypt' ContentService.java` returns 0). Property encryption (`NodePropertyEncryptionService.java:71-100`) explicitly only encrypts node properties. **This is the cleanest candidate for a separate ADR-003** — orthogonal to routing, surfaceable today with no per-tenant infrastructure.
7. **Global KEK across tenants** (`SecretCryptoService.java:44, 101`). Even property encryption is single-key across tenants. Owner: would be ADR-003 if blob encryption ships, or unmoved otherwise.

## Recommendation

### Do enter ADR resolution — but narrowly and via doc updates only

The discovery surfaces two concrete doc-only actions that are appropriate now:

1. **Update ADR-001's status from `Deferred` → `Accepted: Option A`** with the four trigger conditions made explicit as machine-readable exit criteria (e.g., "Open ADR-001-A1 if compliance customer X signs / if dedup hit rate measured < N% / etc."). This is honest about the live state and clarifies how a future re-open would be evaluated.
2. **Open ADR-003 (proposed): Content-at-rest encryption strategy.** Resolves the gap ADR-001 §93 surfaced but did not address. Scope is narrower than ADR-001 and does not require choosing per-tenant routing. Decision options would be: (a) status quo plaintext-on-disk + rely on storage backend SSE; (b) application-layer envelope encryption of blobs with single key version (parallel to property encryption); (c) per-tenant envelope encryption (depends on ADR-001 B or C).

### Do NOT enter ADR resolution that commits to Option B or C now

No trigger condition has materialized. Committing prematurely would require building storage abstraction + migration tooling + backup story, all without a paying compliance signal. Recommend keeping these as documented exit paths in the ADR-001 update, not as in-flight work.

### Update ADR-001 vs new ADR — recommendation

- ADR-001 itself: **update** (status + exit criteria). The concern is the same — storage routing and tenant isolation.
- Content-at-rest encryption: **new ADR-003**. The concern is separable (about cryptography, not routing) and ADR-002 set the precedent for spinning off sibling concerns. ADR-002 stands as proof that this pattern is acceptable to the architecture record.

## Worker brief draft (next step, doc-only, no code)

Below is a draft brief for the next slice. **Do not execute without explicit gate go.** Format mirrors the discovery briefs the user has been using.

### Draft brief: ADR-001 update + ADR-003 creation

```
在 Athena 做 ADR-001 status update + ADR-003 content-at-rest encryption draft 的纯 doc-only slice。
不写代码、不改测试、不碰 .env、不变更存储/加密实现。

必须读：
- docs/adr/ADR-001-storage-routing-tenant-isolation.md  (current)
- docs/adr/ADR-002-tenant-quota-accounting-and-context-boundaries.md  (pattern reference)
- docs/ADR_001_STORAGE_ROUTING_DISCOVERY_20260524.md     (this discovery)

产物 1: 更新 docs/adr/ADR-001-storage-routing-tenant-isolation.md
- Status 从 "Deferred" 改为 "Accepted: Option A"
- 保留原始 Decision Options 表（A/B/C tradeoff）— 是 ADR 历史的一部分
- §"Trigger Conditions to Revisit" 改为 §"Exit Criteria"：把 4 条触发条件改成可观察的明确判据
  (例如 "compliance customer requires data residency in jurisdiction X" / "dedup hit rate measured
   below N% over Y-month window" / etc.)
- 在末尾添加 §"Revisit 2026-05-24"，引用 discovery doc，确认本次复审无新触发条件

产物 2: 新建 docs/adr/ADR-003-content-at-rest-encryption.md
- 体例参照 ADR-002（短、聚焦、Status / Date / Context / Decision / Consequences / Next Steps）
- Context: ADR-001:93 surfaced shared-key consequence; property encryption shipped covers metadata only;
  content blobs remain plaintext at rest
- Decision Options:
  (a) status quo - plaintext on disk, rely on storage-backend SSE only
  (b) application-layer envelope encryption with single key version (matches property encryption posture)
  (c) per-tenant envelope encryption - depends on ADR-001 Option B or C resolving first
- Decision: TBD by gate. Likely (a) for the same "no compliance signal" reasoning, with (b)
  documented as fallback if blob breach risk surfaces; (c) blocked on ADR-001 resolution.
- Cross-reference ADR-001 update + this discovery

验证：
- git diff --check -- . ':!.env'  通过
- git diff --stat -- 'ecm-core/src/main/java/'  空
- git diff --stat -- 'ecm-core/src/test/'        空
- git diff --stat -- 'ecm-frontend/'             空
- 无 CI 触发 (commit message 加 [skip ci])

OOS：
- 不实施 Option B / C 的任何代码骨架
- 不实施 (b) 或 (c) 加密路径
- 不引入 StorageAdapter 接口
- 不写迁移工具
- 不修改测试
- 不动 application*.yml / docker-compose / .env

提交序列（1 commit doc-only）：
- docs(core): update ADR-001 to Accepted Option A and open ADR-003 content-at-rest [skip ci]
```

### When to escalate to a code slice

The discovery does NOT recommend opening a code slice now. A code slice becomes appropriate only when at least one of the following materializes:

- A specific paying customer requires data residency / physical isolation (triggers ADR-001 re-open toward Option B or C).
- A breach analysis surfaces blob-at-rest plaintext as a real exfiltration risk (triggers ADR-003 toward Option b application-layer encryption).
- ADR-002 quota work picks up the physical-footprint accounting model (would benefit from per-tenant subtree but does not strictly require it).

Until one of these fires, the recommended track is "keep the discovery + ADR updates as the durable record" and pivot to a different track entirely (product feature, additional gap-closure item, or another deferred ADR not yet scoped).

## Local verification (this discovery)

```bash
git status --short                                # M .env + this doc only
git diff --check -- . ':!.env'                    # passes
git diff --stat -- 'ecm-core/src/main/java/'     # empty
git diff --stat -- 'ecm-core/src/test/'           # empty
git diff --stat -- 'ecm-frontend/'                # empty
git diff --stat -- 'ecm-core/src/main/resources/' # empty (no yml / changelog touched)
```

Confirmed at time of writing.

## OOS reaffirmation (per brief Q9)

This discovery doc itself respects the OOS list verbatim:

- No change to `ContentService`, `ContentReferenceService`, `TenantWorkspaceScopeService`, `TenantQuotaService`, `SecretCryptoService`, `NodePropertyEncryptionService`, or any transfer / receiver service.
- No change to hash algorithm or hash column.
- No new storage backend, no S3 SDK introduction, no MinIO client wiring.
- No change to ACL / tenant path / workspace scope code.
- `.env`, `application*.yml`, `docker-compose*`, Liquibase changelogs all untouched.
- No new `StorageAdapter` interface.

The proposed next-step worker brief (doc-only) preserves the same OOS list. A future code slice would re-derive its own OOS.
