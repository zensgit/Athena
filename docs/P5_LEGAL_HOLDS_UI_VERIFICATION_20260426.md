# Legal Holds Admin UI — Gap #2 Frontend Closeout (design + verification)

## Date
2026-04-26

## Status
Implementation complete. Commit `33a8256` pushed to `origin/main`.
Closes the frontend gap for Gap #2 (Legal Holds). Backend was already complete
(entity, repository, service, controller) since migration 077.

## Scope

Provide an admin-only UI for managing legal holds that prevent modification
or deletion of content in the repository.

---

## Files added / modified

| File | Change |
|------|--------|
| `ecm-frontend/src/services/legalHoldService.ts` | new — typed API client |
| `ecm-frontend/src/pages/LegalHoldsPage.tsx` | new — admin page (~735 lines) |
| `ecm-frontend/src/App.tsx` | + route `/admin/legal-holds` (ROLE_ADMIN) |
| `ecm-frontend/src/components/layout/MainLayout.tsx` | + nav entry "Legal Holds" with LockIcon |

---

## API client — `legalHoldService.ts`

Class-instance singleton following the `ruleService` pattern. Exported types:

```typescript
LegalHoldStatus = 'ACTIVE' | 'RELEASED'
LegalHoldSummary { id, name, description?, status, itemCount, createdBy?, createdDate?, releasedBy?, releasedAt? }
LegalHoldItem    { nodeId, nodeName?, nodeType?, nodePath?, addedAt?, addedBy? }
LegalHoldDetail  { ...LegalHoldSummary, releaseComment?, items: LegalHoldItem[] }
```

Six methods matching the six backend endpoints exactly:
- `listHolds()` → `GET /legal-holds`
- `getHold(holdId)` → `GET /legal-holds/{holdId}`
- `createHold({ name, description? })` → `POST /legal-holds`
- `addItems(holdId, { nodeIds })` → `POST /legal-holds/{holdId}/items`
- `removeItem(holdId, nodeId)` → `DELETE /legal-holds/{holdId}/items/{nodeId}`
- `releaseHold(holdId, { comment? })` → `POST /legal-holds/{holdId}/release`

---

## Page design — `LegalHoldsPage.tsx`

### Layout
Master-detail split: 4/8 Grid on md+ screens.

Left panel: list of holds. Each row shows:
- Name, status chip (ACTIVE=error/red, RELEASED=default/grey)
- Item count + creator (caption)
- Click → loads detail into right panel

Right panel: hold detail (loaded on selection):
- Name + status chip + description
- Created by/date grid; Released by/date/comment if RELEASED
- "Add Nodes" + "Release Hold" buttons (visible for ACTIVE holds only)
- Items list: nodeName (or UUID fallback), nodeType chip, path, addedAt/addedBy
  - Per-item Delete button (ACTIVE holds only)

### Dialogs (local sub-components)

| Dialog | Triggers | Fields |
|--------|---------|--------|
| `CreateHoldDialog` | "Create Hold" button | name (required), description (optional) |
| `AddNodesDialog` | "Add Nodes" button | multiline textarea (one UUID per line or comma-separated); count preview |
| `ReleaseHoldDialog` | "Release Hold" button | warning Alert + optional release comment |

### Node picker rationale
`FolderTree` with `variant="picker"` only surfaces folder-type children. Legal
holds can target individual documents; a UUID textarea is the appropriate admin
tool. The textarea splits on `[\n,]+` and strips whitespace; count is shown live.

### State management
All mutations apply the `LegalHoldDetail` returned by the API to local state
(patch summary list + set detail) — no redundant refetch on each operation.
`toast.success`/`toast.error` feedback on every action.

---

## Routing and navigation

Route: `/admin/legal-holds` — `ROLE_ADMIN` required (same guard as all other admin routes).

Nav: inserted in MainLayout admin menu block between "Records Management" and
"Template Engine". Uses `Lock as LockIcon` from `@mui/icons-material` (distinct
from `Gavel` used by Records Management).

---

## TypeScript verification

```
cd ecm-frontend && npx tsc --noEmit 2>&1 | grep -v node_modules
# → (no output — zero source-file errors)
```

---

## Backend API reference (already shipped)

| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /api/v1/legal-holds` | ROLE_ADMIN | List all active + released holds (tenant-scoped) |
| `GET /api/v1/legal-holds/{holdId}` | ROLE_ADMIN | Full hold with items |
| `POST /api/v1/legal-holds` | ROLE_ADMIN | Create hold |
| `POST /api/v1/legal-holds/{holdId}/items` | ROLE_ADMIN | Add node IDs to hold |
| `DELETE /api/v1/legal-holds/{holdId}/items/{nodeId}` | ROLE_ADMIN | Remove node from hold |
| `POST /api/v1/legal-holds/{holdId}/release` | ROLE_ADMIN | Release hold with optional comment |

`assertOperationAllowed(Node, operation)` is called by `TrashService` and
`NodeService` to block deletion/move of held nodes. This enforcement was shipped
with the backend and is unaffected by this frontend change.

---

## Security

- Route guarded by `ROLE_ADMIN` in frontend + service-level `requireAdmin()` in backend
- Released holds are read-only in the UI (Add Nodes and Release buttons hidden)
- All mutations validated server-side; client is presentational only
