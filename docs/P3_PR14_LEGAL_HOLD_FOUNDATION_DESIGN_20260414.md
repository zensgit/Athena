# P3 PR-14 Legal Hold Foundation Design

## Date
- 2026-04-14

## Scope
- Deliver a backend-only legal hold foundation before disposition work starts.
- Persist first-class holds and held-node membership.
- Block the highest-risk local destructive repository paths:
  - `NodeService.deleteNode(...)`
  - `NodeService.moveNode(...)`
  - `FolderService.deleteFolder(...)`
  - `TrashService.moveToTrash(...)`
  - `TrashService.permanentDelete(...)`
  - `TrashService.emptyTrash()`
  - `TrashService.purgeOldTrashItems()`
  - `VersionService.deleteVersion(...)`
- Keep frontend hold authoring, archive/transfer enforcement, and disposition integration out of this slice.

## Data Model
- New Liquibase change:
  - `077-create-legal-holds.xml`
- New tables:
  - `legal_holds`
    - first-class hold aggregate with `ACTIVE` / `RELEASED` state
    - release metadata: `released_at`, `released_by`, `release_comment`
    - standard soft-delete / audit columns via `BaseEntity` shape
  - `legal_hold_items`
    - hold-to-node junction
    - snapshot fields for `node_type` and `node_path`
    - unique `(hold_id, node_id)` constraint to prevent duplicate attachments

## Runtime Components
- `LegalHoldService`
  - admin-only hold create/get/list/add/remove/release operations
  - subtree-aware blocking evaluation via:
    - `findBlockingActiveHolds(Node)`
    - `assertOperationAllowed(Node, operation)`
- `LegalHoldController`
  - `GET /api/v1/legal-holds`
  - `GET /api/v1/legal-holds/{holdId}`
  - `POST /api/v1/legal-holds`
  - `POST /api/v1/legal-holds/{holdId}/items`
  - `DELETE /api/v1/legal-holds/{holdId}/items/{nodeId}`
  - `POST /api/v1/legal-holds/{holdId}/release`

## Guard Semantics
- A destructive operation is blocked when:
  - the target node itself is held
  - an ancestor folder is held
  - the target folder subtree contains a held descendant
- `emptyTrash()` performs a root-item preflight check before deleting anything.
  - This avoids partial empty-trash behavior when one held subtree is present.
- `purgeOldTrashItems()` skips held items and logs the rejection instead of failing the entire purge run.
- `deleteVersion(...)` blocks off the owning document.
  - This keeps historical content protected while the document remains under hold.

## Injection Strategy
- Existing services were not constructor-expanded for this slice.
- `LegalHoldService` is injected into destructive services as `@Autowired @Lazy` optional field state.
- This keeps the patch small and avoids churn across the large existing unit-test surface.

## Security Model
- Hold authoring APIs are `ROLE_ADMIN` only in this slice.
- No new `ROLE_RECORDS_MANAGER` was introduced.
- Tenant workspace scoping still applies when resolving visible held items for DTO output.

## Deferred
- Archive enforcement:
  - `ContentArchiveService`
  - `ArchivePolicyService`
- Transfer / replication enforcement:
  - outbound replication
  - inbound overwrite paths
  - loopback transfer
- Bulk import overwrite-as-delete paths
- Frontend hold management surface
- Disposition integration and records-management semantics
- Dedicated hold lifecycle events beyond entity audit fields and logs

## Acceptance Intent
- `PR-14` closes the minimum repository-governance gap needed before `PR-15`.
- It does not claim full enterprise legal-hold coverage across every mutation seam in Athena.
