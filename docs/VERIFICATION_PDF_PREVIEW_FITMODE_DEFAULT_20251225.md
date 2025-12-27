## PDF Preview Default Fit Mode Verification (2025-12-25)

### Scope
- Ensure PDF preview defaults to Fit to height.

### Build/Deploy
- `docker compose build ecm-frontend`
- `docker compose up -d ecm-frontend`

### Verification Steps (MCP)
1. Logged in at `http://localhost:5500/`.
2. Opened `J0924032-02上罐体组件v2-模型.pdf` via row actions → View.
3. Confirmed toolbar shows `Fit to height` as the active fit mode (accessibility snapshot).

### Result
- PDF preview now defaults to Fit to height on first load.
