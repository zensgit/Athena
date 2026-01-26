# Content Storage Permission Fix (2026-01-25)

## Issue
Playwright uploads failed with:
- `Content storage failed: /var/ecm/content/2026/01/25`
- HTTP 400 on file uploads

## Cause
`/var/ecm/content` was owned by UID 999 (`systemd-journal`) inside `athena-ecm-core-1`,
so the app user (`app`, UID 995) could not write.

## Fix (Runtime)
Run inside the container:

```
docker exec -u 0 athena-ecm-core-1 chown -R app:app /var/ecm/content
```

Create missing date folders if needed:

```
docker exec athena-ecm-core-1 mkdir -p /var/ecm/content/2026/01/25
```

## Verification
- Write test succeeds:

```
docker exec athena-ecm-core-1 sh -c 'touch /var/ecm/content/.write_test && echo ok'
```

- Playwright uploads pass (see `docs/VERIFICATION_FRONTEND_E2E_MAIL_AUTOMATION_20260125.md`).

## Notes
If `ecm_content` is a named Docker volume, the ownership may persist across restarts.
The `ecm-core` image now includes an entrypoint that corrects ownership on startup.
For legacy volumes, you may still need the one-time fix above.
