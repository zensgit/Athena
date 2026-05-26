# Alfresco Service-Contract Parity — read-only 对标

Date: 2026-05-26 · Reference: `reference-projects/alfresco-community-repo` (Alfresco Community, 346M git clone) · Scope: **service-contract parity** of Athena's `com.ecm.core.alfresco.*` shim vs Alfresco's public Foundation-API service interfaces. **Read-only; no code change.**
Companion (broad): `docs/ALFRESCO_PARITY_BENCHMARK_REFRESH_20260526.md` — capability-level benchmark + buyer-signal-gated options. This doc is its narrow, service-contract-level deep-dive.

## 0. Load-bearing finding — the shim is unused, self-contained scaffolding

Before any parity judgment: **Athena's `alfresco/*` package is not a real Alfresco binding and is consumed by nothing.** Evidence:

- **No Alfresco dependency / types.** `ecm-core/pom.xml` (and root pom) have **zero** Alfresco artifacts. `AlfrescoNodeService` imports `com.ecm.core.service.NodeService` + `com.ecm.core.entity.{Document,Folder,Node}` — **no `import org.alfresco.*`**. The `NodeRef`/`QName`/`Path` it uses are Athena's own, defined in `AlfrescoModel.java`. So this is an Alfresco-**shaped** facade over Athena's own services/types, not a link to Alfresco libraries.
- **No consumer, anywhere.** All 5 shim beans (`@Service`/`@Component`, incl. named `alfrescoNodeService`/`alfrescoContentService`) have **zero** references outside the package — verified across: class-name grep, string-name bean lookups + `@Qualifier`, XML and `@Bean` factory wiring, and a whole-repo sweep (excl. `reference-projects`). All empty.

➡️ **Measuring this shim's parity against Alfresco's 57-service Foundation API is the wrong yardstick.** "Completing" it would mean recreating the Alfresco Foundation API — which `CLAUDE.md` explicitly forbids ("bridge existing capabilities, do not recreate Alfresco-shaped subsystems"). The parity table below is provided as requested, but the disposition (§3–4) is what matters.

## 1. ServiceRegistry coverage

`org.alfresco.service.ServiceRegistry` exposes **57** `getXxxService()` accessors (`repository/.../org/alfresco/service/ServiceRegistry.java`). Athena's `AlfrescoServiceRegistry` exposes **4**: Node, Content, Search, Permission.

- Covered (4): NodeService, ContentService, SearchService, PermissionService.
- Absent (53): Version, FileFolder, Namespace, CheckOutCheckIn, Copy, Action, Rule, Category, Dictionary, Authority, Person, Site, Audit, Tagging, Rendition, Thumbnail, Workflow, Lock, Ownable, Mimetype, Template, Form, Rating, Invitation, CMISDictionary, CMISQuery, Imap, WebDav, … — **do NOT propose wiring these; that is recreate-bait.**

## 2. Method-level parity (the 4 shimmed services)

| Service | Athena shim covers | Parity verdict | Notable Alfresco methods absent |
|---|---|---|---|
| **NodeService** | createNode, get/setProperties, getType, moveNode, copyNode¹, deleteNode, getChildAssocs(basic), exists, getPath | **partial** (~10 of ~60) | aspects (add/remove/has/get), peer associations (create/remove/getTarget/getSource), per-property ops (get/set/remove/addProperties), stores, getPrimaryParent/getParentAssocs, restore, findNodes, filtered/paged child-assoc variants |
| **ContentService** | getReader (real), getWriter→putContent (real) | **partial + 2 stubs** | `transform` → `UnsupportedOperationException` (**stub**, `:125`); `getContentOutputStream` → `UnsupportedOperationException` (**stub**, `:103`); getRawReader, getTempWriter, requestContentDirectUrl (direct-access URLs), storage-properties/archive, store-space |
| **PermissionService** | hasPermission, setPermission, deletePermission, getAllSetPermissions, setInheritParentPermissions | **partial** (~5 of ~20) | getPermissions, getInheritParentPermissions (getter), clearPermission, deletePermissions, store-level perms, getReaders/getReadersDenied, getAuthorisations, getSettablePermissions, hasReadPermission |
| **SearchService** | query(SearchParameters), query(store,lang,query), addStore/addSort, getNodeRefs/hasMore | **partial** (2 of 4 query forms) | query w/ QueryParameterDefinition, query by QName id; XPath selectNodes/selectProperties; contains/like text search |

¹ `copyNode` is an Athena convenience — Alfresco keeps copy in a separate `CopyService`, not `NodeService`.

## 3. Tie to matrix C4 (do not change the matrix)

C4 ("Alfresco compat: transformation + direct-stream are stubs", `AlfrescoContentService.java:103,125`) is real, **but the two `UnsupportedOperationException` stubs live in code that nothing calls today.** So C4's actual blast radius is **lower than the matrix wording implies** — it can only bite a future Alfresco Foundation-API drop-in that wires this shim, which does not exist. The matrix already dispositions C4 correctly ("must-fix iff Alfresco drop-in; else acceptable"); this 对标 sharpens the "iff" with hard evidence. **No matrix change proposed.**

## 4. The parity that actually matters: CMIS (consumed, standards-based)

Athena's real, **consumed** Alfresco-interop surface is **CMIS** (OASIS standard) — `CmisAtomPubController` + `CmisBrowserController` over 13 services (`CmisAclService`, `CmisQueryService`, `CmisContentVersioningService`, `CmisChangeLogService`, …). The gap-closure roadmap (`CLAUDE.md`) already marks **CMIS COMPLETE** (10 capabilities: secondary types, version history, change log, ACL mapping, relationships, renditions, CONTAINS()+IN_TREE(), object CRUD, content streams, checkout/checkin, type system). **CMIS — not the Foundation-API shim — is the Alfresco-interop contract worth holding to parity**, and it is already at it.

## 5. Honest conclusion (matches the Refresh-4 pause cadence)

**No demand-backed product slice falls out of this 对标.** What falls out:

- **(a) A hygiene/clarity item — owner's binary call, not my recommendation:** the `alfresco/*` shim is deliberate-but-inert Alfresco-shaped scaffolding. Either (i) **document** it as intentional future-binding scaffolding (a header comment + a one-line note in the matrix/handoff), or (ii) **delete** it as dead code. Both are defensible; deletion is a real option given the product line is paused, but the call belongs to the owner. This is hygiene, ~tiny, not a feature track.
- **(b) A contingent track, explicitly out today:** if an Alfresco **Foundation-API drop-in** becomes a real customer signal, that is a large separate track — and even then the design must respect "bridge, don't recreate" (likely: widen CMIS / a thin adapter for the specific needed calls, not a 57-service reimplementation). Not opened.

This is consistent with Refresh 4: code/reference inspection alone is not yielding demand-backed gaps; real ones will come from operator/customer signals.

## 6. Verification (this 对标)

- "No consumer" verified 4 ways (class-name, string-bean, XML/@Bean, whole-repo) — all empty.
- "No Alfresco dependency / types" verified (poms + imports).
- Alfresco interface surfaces read from the reference clone (`ServiceRegistry` 57 accessors; `NodeService`/`ContentService`/`PermissionService`/`SearchService` method lists).
- `.env` untouched; nothing committed.
