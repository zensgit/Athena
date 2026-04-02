# Phase 368U — Shared Link Operator Surface Convergence

> **Scope**: Wire 368T access stats/reactivate/admin list into ShareLinkManager + AdminDashboard
> **Date**: 2026-03-30

---

## 1. Problem Statement

Phase 368T delivered the backend (access audit log, reactivate, admin list, stats) but the
frontend did not consume these capabilities:

| Gap | Where |
|-----|-------|
| No reactivate button in ShareLinkManager | Deactivated links had no recovery path in UI |
| No access stats/log in ShareLinkManager | Operators couldn't see who accessed or how many times |
| No status chip (Active/Expired/Inactive) | Hard to triage link health at a glance |
| No admin share link panel in AdminDashboard | System-wide oversight required separate API calls |
| Controller endpoints untested at HTTP level | 368T only had service-layer tests |

## 2. What Was Built

### ShareLinkManager.tsx — Rewrite

| Feature | Implementation |
|---------|---------------|
| **Status chip** | Active (green), Expired (warning), Inactive (grey) per row |
| **Reactivate button** | PlayArrow icon on inactive links → calls `reactivateLink(token)` |
| **Expandable access panel** | Click chevron → loads stats + log inline via `getAccessStats` + `getAccessLog` |
| **Access stats** | Total / Successful / Failed counts displayed as metric cards |
| **Access log table** | Last 10 entries: time, IP, result (OK/Fail chip), failure reason |
| **Protection badges** | PWD / IP chips on access count column |

### AdminDashboard.tsx — New "Share Links" Tab (tab 3)

| Feature | Implementation |
|---------|---------------|
| **Tab entry** | "Share Links" tab added to admin tabs |
| **ShareLinksAdminPanel** | Table of all share links system-wide via `listAllLinks()` |
| **Columns** | Name, Node, Created By, Status, Permission, Expires, Access count, Protection |
| **Refresh** | Refresh button reloads list |

### Controller Tests — 4 New HTTP-Level Tests

Verifying the 368T endpoints return correct JSON shapes.

## 3. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `test/controller/ShareLinkControllerEnhancementTest.java` | 4 MockMvc tests |

### Modified Files

| File | Change |
|------|--------|
| `components/share/ShareLinkManager.tsx` | Full rewrite: +reactivate, +expandable stats/log, +status chips |
| `pages/AdminDashboard.tsx` | +Tab 3 "Share Links", +ShareLinksAdminPanel component |

### NOT Modified

All preview/rendition/search/ops-governance files, backend service/controller unchanged.

## 4. Operator Surface Matrix

| Capability | ShareLinkManager | AdminDashboard |
|------------|:----------------:|:--------------:|
| View links for node | Row table | — |
| Create link | Form dialog | — |
| Copy URL | Copy button | — |
| Deactivate | Block button | — |
| **Reactivate** | PlayArrow button | — |
| Delete | Delete button | — |
| **Status chip** | Per row | Per row |
| **Access stats** | Expandable panel | — |
| **Access log** | Expandable panel (last 10) | — |
| **PWD/IP badges** | Per row | Per row |
| **System-wide list** | — | Full table |
| **Refresh** | On dialog open | Button |
