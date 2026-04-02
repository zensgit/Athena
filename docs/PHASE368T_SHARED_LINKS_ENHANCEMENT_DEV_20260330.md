# Phase 368T — Shared Links Enhancement

> **Scope**: DB migration, access audit log, reactivate, admin list, access stats, frontend operator surface
> **Date**: 2026-03-30

---

## 1. Problem Statement

The ShareLink stack had comprehensive code (entity, service, controller, frontend) but:

| Gap | Risk |
|-----|------|
| **No DB migration** — share_links table never created | Application fails on first persist |
| **No access audit log** | Cannot audit who accessed, when, from what IP |
| **No reactivate endpoint** | Deactivated links are permanently dead |
| **No admin list-all** | No oversight of all share links system-wide |
| **No access statistics** | Cannot measure link usage |
| **Frontend missing lifecycle methods** | Cannot reactivate, view access log, or stats |

## 2. What Was Built

### DB Migration 044 (critical fix)
Creates **two tables**:

```sql
share_links (id, token UNIQUE, node_id FK, created_by, created_at,
             expiry_date, password_hash, max_access_count, access_count,
             is_active, name, permission_level, last_accessed_at, allowed_ips)
  + 4 indexes (token, node_id, created_by, expiry_date)

share_link_access_log (id, share_link_id FK, accessed_at, client_ip,
                       user_agent, success, failure_reason)
  + 2 indexes (share_link_id, accessed_at)
```

### Access Audit Log
- New entity `ShareLinkAccessLog` — records every access attempt
- Records: shareLink, timestamp, clientIp, userAgent, success, failureReason
- Logged on: successful access, expired link, wrong password, IP restriction

### New Service Methods (4)

| Method | Description |
|--------|-------------|
| `reactivateShareLink(token)` | Re-enable a deactivated link (creator/admin) |
| `listAllShareLinks()` | Admin-only list of all share links |
| `getAccessLog(token)` | Access audit trail (creator/admin) |
| `getAccessStats(token)` | Total/successful/failed counts |

### New Controller Endpoints (4)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/share/{token}/reactivate` | Reactivate deactivated link |
| GET | `/api/share/admin/all` | Admin list all share links |
| GET | `/api/share/{token}/access-log` | Access audit trail |
| GET | `/api/share/{token}/access-stats` | Access statistics |

### Frontend Service Methods (4 new)

```typescript
reactivateLink(token)   → POST /share/{token}/reactivate
listAllLinks()          → GET /share/admin/all
getAccessLog(token)     → GET /share/{token}/access-log
getAccessStats(token)   → GET /share/{token}/access-stats
```

### Frontend Types (2 new)

```typescript
interface AccessLogEntry { id, accessedAt, clientIp?, userAgent?, success, failureReason? }
interface AccessStats { totalAccesses, successfulAccesses, failedAccesses }
```

## 3. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `entity/ShareLinkAccessLog.java` | Access audit log entity |
| `repository/ShareLinkAccessLogRepository.java` | Access log queries |
| `db/changelog/changes/044-create-share-links-table.xml` | Both tables + indexes |
| `test/service/ShareLinkEnhancementTest.java` | 9 focused tests |

### Modified Files

| File | Change |
|------|--------|
| `service/ShareLinkService.java` | +accessLogRepo dependency; access logging in accessShareLink; +4 new methods |
| `controller/ShareLinkController.java` | +4 new endpoints + AccessLogEntryDto |
| `services/shareLinkService.ts` | +4 new methods + AccessLogEntry/AccessStats types |
| `test/service/ShareLinkServiceTest.java` | +accessLogRepo mock for constructor compatibility |
| `db/changelog/db.changelog-master.xml` | +044 |

### NOT Modified

All preview/rendition/search/ops-governance files untouched.
