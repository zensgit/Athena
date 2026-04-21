# P5 PR-101 RM Report Preset Schedule Full-Stack Smoke — Verification

## Date
2026-04-21

## Verified Scope

- the shipped preset scheduled-delivery chain now has one real full-stack/admin smoke
- the smoke runs against the current working tree frontend on `http://127.0.0.1:3000`
- the backend container was rebuilt from the current working tree so `PR-95` routes are actually present on `:7700`
- readiness is now gated on `/actuator/health -> status == UP`
- saving schedule config persists through the real backend
- `Deliver now` produces a real execution ledger entry and a real CSV artifact in the target folder

## Commands

### Rebuild backend container from current code

```bash
docker compose build --build-arg SKIP_LIBREOFFICE=true ecm-core
docker compose up -d ecm-core
```

Result:

- rebuild completed successfully
- `athena-ecm-core-1` restarted on `:7700`

### Probe schedule/execution routes on the running backend

Result:

- `GET /api/v1/records/report-presets/{id}/schedule` → `200`
- `GET /api/v1/records/report-presets/{id}/executions?limit=2` → `200`

Note:

- before rebuilding, the long-running `:7700` stack returned `404` for both routes, so the backend container was demonstrably stale relative to the current code

### Current-working-tree frontend

```bash
cd ecm-frontend && npm start
```

Result:

- dev server compiled successfully on `http://localhost:3000`

### Full-stack Playwright smoke

```bash
cd ecm-frontend && ECM_UI_URL=http://127.0.0.1:3000 ECM_API_URL=http://127.0.0.1:7700 KEYCLOAK_URL=http://127.0.0.1:8180 npx playwright test e2e/rm-report-preset-schedule.spec.ts --workers=1
```

Result:

- `1 passed`
- `RM report preset schedule can be configured from Records Management (full-stack)`

### Static diff check

```bash
git diff --check
```

Result:

- passed

## Notes

- this slice verifies the real operator chain rather than a mocked API seam
- local `spring-boot:run` remains environment-drifted in this execution environment because the process cannot reach the host PostgreSQL mapping, so the authoritative full-stack proof for this slice is the rebuilt Docker backend plus the current working tree frontend
- no new backend endpoint or frontend runtime surface was added beyond the E2E smoke and readiness-helper hardening
