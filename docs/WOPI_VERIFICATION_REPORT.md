# WOPI Verification Report

Date: 2025-12-20

## Scope
Validated Office preview + online view and audit logging for WOPI-based editing.

## Environment
- Frontend: http://localhost:5500
- API: http://localhost:7700
- Keycloak: http://localhost:8180/realms/ecm
- Script: `./scripts/verify.sh --wopi-only`

## Test Asset
- Document: `工作簿1.xlsx`
- Document ID: `9cd1becc-98dd-4d31-abc4-ad3ef1ba7d03`

## Steps Executed (Automated)
1. Login via Keycloak (`admin/admin`).
2. Locate the document via search API and UI search.
3. Open preview and confirm WOPI iframe is present.
4. Confirm preview menu contains `View Online` and `Edit Online`.
5. Open `View Online` (read-only) and confirm WOPI iframe loads.
6. Request WOPI write URL and execute `PutFile`.
7. Validate `WOPI_UPDATED` appears in audit logs.

## Results
- ✅ Preview iframe loaded.
- ✅ `View Online` and `Edit Online` menu items present.
- ✅ Read-only WOPI editor opened successfully.
- ✅ WOPI write completed.
- ✅ Audit log contains `WOPI_UPDATED`.

## Evidence
Log excerpt:
```
[verify] Preview iframe src: present
[verify] Menu contains View Online / Edit Online
[verify] Editor iframe src: present
[verify] Posting WOPI update (PutFile)
[verify] Checking audit logs for WOPI_UPDATED
[verify] Audit log entry found: WOPI_UPDATED at 2025-12-20T00:48:01.884572
```

## Logs
- `/Users/huazhou/Downloads/Github/Athena/tmp/20251220_084736_verify-wopi.log`

## Notes
- The verification uses `scripts/verify-wopi.js` embedded in the `verify.sh` workflow.
- If WOPI is unavailable or misconfigured, expect `WOPI editor is disabled` or `No WOPI action available` responses from the API.
