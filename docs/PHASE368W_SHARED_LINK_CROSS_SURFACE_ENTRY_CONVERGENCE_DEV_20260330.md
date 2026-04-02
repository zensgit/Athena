# Phase 368W — Shared Link Cross-Surface Entry Convergence

> **Scope**: Fix admin open-node route, add share entry to DocumentPreview + SearchResults
> **Date**: 2026-03-30

---

## 1. Problem Statement

| Gap | Impact |
|-----|--------|
| AdminDashboard "Open node" linked to `/files?nodeId=...` | **Route doesn't exist** — actual file browser route is `/browse/:nodeId` |
| DocumentPreview toolbar has no Share entry | Users previewing a document must close preview, right-click in FileList, then Share |
| SearchResults per-row actions have no Share button | Users must navigate to file browser to share a search result |

## 2. What Was Fixed/Added

### Route Fix: AdminDashboard

```diff
- window.location.href = `/files?nodeId=${nodeId}`;
+ window.location.href = `/browse/${nodeId}`;
```

### DocumentPreview.tsx — "Share" Menu Item

Added to the `MoreVert` action menu, after "Download":

```tsx
{canWrite && node?.nodeType === 'DOCUMENT' && (
  <MenuItem onClick={() => {
    previewDispatch(setSelectedNodeId(nodeId));
    previewDispatch(setShareLinkManagerOpen(true));
    handleMenuClose();
  }}>
    <Share fontSize="small" /> Share
  </MenuItem>
)}
```

- Uses `useAppDispatch` to open the ShareLinkManager dialog
- Only shown for documents when user has write permission
- ShareLinkManager is already rendered in MainLayout — dispatch opens it

### SearchResults.tsx — "Share" Per-Row Button

Added after the "Download" button in the expanded result card:

```tsx
{canWrite && isDocumentNode(node) && (
  <Button size="small" startIcon={<ShareIcon />} onClick={() => handleOpenShareLink(node)}>
    Share
  </Button>
)}
```

Handler dispatches `setSelectedNodeId` + `setShareLinkManagerOpen`.

## 3. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `test/controller/ShareLinkCrossSurfaceTest.java` | 4 tests verifying response shapes for cross-surface consumption |

### Modified Files

| File | Change |
|------|--------|
| `pages/AdminDashboard.tsx` | Fix navigateToNode route: `/files?nodeId=` → `/browse/` |
| `components/preview/DocumentPreview.tsx` | +Share icon import, +useAppDispatch, +Share menu item |
| `pages/SearchResults.tsx` | +ShareIcon import, +uiSlice actions import, +handleOpenShareLink handler, +Share button per row |

### NOT Modified

Backend unchanged. All preview/rendition/search/ops-governance **backend** files untouched. (SearchResults.tsx is a frontend **page**, not a search service/controller.)
