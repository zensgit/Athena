# Smoke Test Verification (2025-12-25)

## Environment
- Backend: http://localhost:7700
- Token: admin (tmp/admin.access_token)

## Command
```
./scripts/smoke.sh
```

## Results
- ✅ Health, Metrics, System Status, License
- ✅ Audit export + retention
- ⚠️ ClamAV not ready within 30s; EICAR skipped
- ✅ Users/Groups CRUD
- ✅ Rules (auto-tag) + Scheduled rules + audit logs
- ✅ PDF preview API
- ✅ Favorites, Correspondents, Search + facets
- ✅ WOPI (CheckFileInfo/GetFile/PutFile/Lock/Unlock) + version increment
- ✅ ML health
- ✅ Copy/Move
- ✅ Share link
- ✅ Tags/Categories + advanced search facets
- ✅ Workflow start/complete
- ✅ Trash move/restore
- ✅ Cleanup of test folder

## Conclusion
Smoke test finished successfully with the only warning being ClamAV not ready in 30s.
