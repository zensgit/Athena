# PHASE1 P63 - Audit Logs Filter by Node + Deep Link Verification

Date: 2026-02-09

## Preconditions

- Docker stack running:
  - UI: `http://localhost:5500`
  - API: `http://localhost:7700`

If you changed frontend/backend code, rebuild:

```bash
bash scripts/restart-ecm.sh
```

## Automated Verification (Playwright)

Run targeted E2E:

```bash
cd ecm-frontend
npx playwright test e2e/audit-node-filter.spec.ts
```

### Expected

- Creates a folder + uploads a text file
- Waits for an audit record for the uploaded document (`/api/v1/analytics/audit/search?nodeId=...`)
- In Browse list, opens the row actions menu and clicks `View Audit`
- Verifies:
  - URL is `/admin?auditNodeId=<docId>`
  - Admin filter bar shows `Node ID = <docId>`
  - Audit activity includes the filename

### Result

- `1 passed`

