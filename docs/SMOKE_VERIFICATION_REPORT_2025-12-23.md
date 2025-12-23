# Smoke Verification Report (2025-12-23)

## Scope
- Backend smoke test with authenticated admin token
- Target API: http://localhost:7700

## Command
```
ECM_TOKEN=<admin token> scripts/smoke.sh
```

## Result
- Status: PASS

## Highlights
- Health/metrics/system status/license/sanity/analytics/audit: OK
- Antivirus: enabled + healthy; EICAR rejected (HTTP 400)
- RBAC: admin roles present
- Admin users/groups APIs: create/list/assign/remove/delete OK
- Rules: list + create + upload trigger + auto-tag + cleanup OK
- Scheduled rules: cron validation + manual trigger + audit summary OK
- PDF preview: OK (1 page)
- WOPI: CheckFileInfo/GetFile/LOCK/PutFile/UNLOCK OK; version incremented 1.0 -> 1.1
- Search: indexed primary + correspondent test docs; advanced + faceted search OK
- Saved search: create/run/delete OK
- Favorites: add/list/remove OK
- Share link: created (token saved to `tmp/smoke.share_token`)
- Tags/Categories: assigned + facet validation OK
- Workflow: approval started/completed OK
- Trash: move/list/restore OK
- Cleanup: test folder removed (recursive)

## Notes
- Admin token obtained via Keycloak password grant (`client_id=unified-portal`, realm `ecm`).
