# P5 PR-113 RM Summary Preset Schedule Full-Stack Smoke Verification

## Scope Verified

- full-stack admin smoke for the shipped summary-only preset schedule/export chain
- live browser proof for:
  - summary-only preset row exposes `Export CSV`
  - summary-only preset schedule dialog saves cron + delivery folder
  - summary-only preset `Deliver now` succeeds
  - delivered CSV lands in the selected folder
- existing deliverable preset full-stack smoke remains green in the same spec

## Environment

- frontend: current worktree dev server on `http://127.0.0.1:3000`
- backend: rebuilt current-code docker `ecm-core` on `http://127.0.0.1:7700`
- keycloak: `http://127.0.0.1:8180`

Important note:

- an older backend container initially returned `400` on summary-only `Save schedule`
- authoritative verification was taken only after rebuilding and restarting `ecm-core` from the current local code

## Commands

### Rebuild and restart backend

```bash
docker compose build --build-arg SKIP_LIBREOFFICE=true ecm-core
docker compose up -d ecm-core
```

### Backend health

```bash
curl -s http://127.0.0.1:7700/actuator/health
```

Result:

```text
{"status":"UP"}
```

### Full-stack Playwright

```bash
cd ecm-frontend
ECM_UI_URL=http://127.0.0.1:3000 ECM_API_URL=http://127.0.0.1:7700 KEYCLOAK_URL=http://127.0.0.1:8180 npx playwright test e2e/rm-report-preset-schedule.spec.ts --workers=1
```

Result:

```text
Running 2 tests using 1 worker
  ✓ RM report preset schedule can be configured from Records Management (full-stack)
  ✓ RM summary-only preset can be exported and scheduled from Records Management (full-stack)
  2 passed
```

### Static diff check

```bash
git diff --check
```

Result:

- passed

## Conclusion

`PR-113` is accepted as the first non-mocked full-stack/admin smoke across the shipped summary-only preset export + scheduled-delivery chain.
