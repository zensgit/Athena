# Phase 368V — Shared Link Admin Governance Surface

> **Scope**: Upgrade AdminDashboard Share Links panel from read-only list to full governance workbench
> **Date**: 2026-03-30

---

## 1. Problem Statement

Phase 368U wired the backend capabilities into ShareLinkManager (per-node dialog) but the
AdminDashboard "Share Links" tab (tab 3) was a **read-only table** with no actions, no
filtering, no drill-down, and no node navigation. Administrators needed to use API calls
directly for governance operations.

## 2. What Was Built

### AdminDashboard Share Links Governance Panel

The read-only table was replaced with a full governance workbench:

| Feature | Implementation |
|---------|---------------|
| **Status filter** | Dropdown: All / Active / Inactive / Expired — client-side filter |
| **Creator filter** | Text input — substring match on `createdBy` |
| **Filter count** | Header shows `(filtered/total)` |
| **Deactivate action** | Block icon button per active row → `deactivateLink()` |
| **Reactivate action** | PlayArrow icon button per inactive row → `reactivateLink()` |
| **Delete action** | Delete icon button with confirmation dialog → `deleteLink()` |
| **Open node jump** | OpenInNew icon per row → navigates to `/files?nodeId=...` |
| **Access stats drill-down** | Expand chevron → loads Total/Successful/Failed metrics |
| **Access log drill-down** | Expand chevron → last 15 access log entries (time, IP, result, reason) |
| **Drill-down caching** | Stats + log cached per token — expand again doesn't re-fetch |
| **Status chip** | Active (green), Expired (warning), Inactive (grey) |
| **Protection badges** | PWD / IP chips per row |

### Layout

```
┌─────────────────────────────────────────────────────────────┐
│  Share Links Governance (12/25)          [Creator...] [Status ▾] [Refresh] │
├────┬─────────┬──────────┬─────────┬────────┬──────┬─────┬──────┬──────┬────┤
│ ▸  │ Name    │ Node  ↗  │ Creator │ Status │ Perm │ Exp │ Acc  │ Prot │ Act│
├────┼─────────┼──────────┼─────────┼────────┼──────┼─────┼──────┼──────┼────┤
│ ▸  │ Report  │ rep.pdf↗ │ alice   │ Active │ VIEW │ Nev │ 5/∞  │ PWD  │ ⊘ 🗑│
│ ▾  │ Draft   │ dra.pdf↗ │ bob     │ Inact  │ EDIT │ ... │ 3/10 │ IP   │ ▶ 🗑│
│    ├─────────┴──────────┴─────────┴────────┴──────┴─────┴──────┴──────┴────┤
│    │  Total: 8    Successful: 6    Failed: 2                               │
│    │ ┌──────────────┬──────────┬────────┬───────────────┐                  │
│    │ │ Time         │ IP       │ Result │ Reason        │                  │
│    │ ├──────────────┼──────────┼────────┼───────────────┤                  │
│    │ │ 2026-03-30...│ 10.0.0.1 │ OK     │ -             │                  │
│    │ │ 2026-03-29...│ 10.0.0.2 │ Fail   │ IP restricted │                  │
│    │ └──────────────┴──────────┴────────┴───────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

## 3. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `test/service/ShareLinkGovernanceTest.java` | 10 focused tests for governance workflow |

### Modified Files

| File | Change |
|------|--------|
| `pages/AdminDashboard.tsx` | Full rewrite of ShareLinksAdminPanel: +filters, +actions, +drill-down, +navigation, +Collapse/Tooltip/icon imports |

### NOT Modified

All backend service/controller files unchanged. All preview/rendition/search/ops-governance files untouched.

## 4. Test Inventory

### ShareLinkGovernanceTest.java — 10 tests

```
LifecycleCycle (4):
  ✓ deactivateShareLink sets active=false
  ✓ reactivateShareLink sets active=true after deactivation
  ✓ admin can deactivate other user's link
  ✓ admin can reactivate other user's link

DeleteGovernance (2):
  ✓ creator can delete own link
  ✓ non-creator without permission cannot delete

AccessLogDrillDown (3):
  ✓ getAccessLog returns entries ordered by time desc
  ✓ non-creator non-admin cannot view access log
  ✓ getAccessStats aggregates correctly

AdminListFiltering (1):
  ✓ listAllShareLinks returns all links for admin
```
