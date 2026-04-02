# Phase 368X — Node Association Operator Surface

> **Scope**: Frontend AssociationManager dialog for peer/secondary-child CRUD, FileList context menu entry
> **Date**: 2026-03-30

---

## 1. Problem Statement

Phase 368R delivered peer association and secondary-child backend endpoints but the
frontend had **no UI surface** to manage them. Users had to call API endpoints directly.

## 2. What Was Built

### AssociationManager Dialog Component

A new tabbed dialog (`components/dialogs/AssociationManager.tsx`) with 4 tabs:

| Tab | Content | Actions |
|-----|---------|---------|
| **Targets** | Outgoing peer associations | Add (node ID + assocType), Remove, Open node |
| **Sources** | Incoming peer associations | Open node (read-only) |
| **Sec. Children** | Secondary children (multi-filing) | Add (child ID), Remove, Open node |
| **Sec. Parents** | Secondary parents | Open node (read-only) |

Each tab shows a table with columns: Name, Type (chip), Path, Actions.

**Add forms**: Target tab has node ID + assocType inputs; Secondary Children tab has child ID input.

**Open in browser**: Each row has an OpenInNew icon that opens `/browse/:nodeId` in a new tab.

### Redux State + MainLayout Wiring

- `uiSlice.ts`: +`associationManagerOpen` state + `setAssociationManagerOpen` action
- `MainLayout.tsx`: Renders `<AssociationManager>` from Redux state, same pattern as ShareLinkManager

### FileList Context Menu Entry

New "Associations" menu item in the right-click context menu, available for all documents. Uses AccountTree icon. Dispatches `setSelectedNodeId` + `setAssociationManagerOpen`.

### Controller Endpoint Tests

7 MockMvc tests verifying all association endpoint response shapes.

## 3. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `components/dialogs/AssociationManager.tsx` | 4-tab dialog: targets/sources/sec-children/sec-parents |
| `test/controller/NodeControllerAssociationEndpointTest.java` | 7 focused endpoint tests |

### Modified Files

| File | Change |
|------|--------|
| `store/slices/uiSlice.ts` | +`associationManagerOpen` state, action, initial, closeAll |
| `components/layout/MainLayout.tsx` | +AssociationManager rendering + imports |
| `components/browser/FileList.tsx` | +"Associations" context menu item + handler |

### NOT Modified

Backend unchanged. All preview/rendition/search/ops-governance files untouched.
