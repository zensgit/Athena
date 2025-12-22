# Antivirus Upload/Scan Verification

## Summary
- ClamAV container was restarted to clear an unhealthy state (clamd socket refused).
- System status now reports antivirus enabled/available/healthy.
- EICAR test upload correctly rejected (HTTP 400), confirming scan pipeline enforcement.

## Environment
- ECM API: http://localhost:7700
- Keycloak: http://localhost:8180
- ClamAV container: `athena-clamav-1`

## Steps Executed
1. Restarted ClamAV container: `docker restart athena-clamav-1`
2. Checked container health: `docker inspect -f '{{.State.Health.Status}}' athena-clamav-1`
3. Generated admin token: `bash scripts/get-token.sh admin admin`
4. Queried system status: `GET /api/v1/system/status`
5. Resolved uploads root folder id: `GET /api/v1/folders/roots`
6. Uploaded EICAR test file to `/api/v1/documents/upload?folderId=<uploads-root>`

## Results
- ClamAV health: **healthy**
- System status antivirus: **enabled=true**, **available=true**, **status=healthy**
- EICAR upload response: **HTTP 400** (expected rejection)

## Evidence
- Log: `tmp/20251222_121601_av-verify.log`
