## PDF Preview Layout Verification (2025-12-25)

### Scope
- Confirm PDF preview container fills available height after layout change.

### Build/Deploy
- `docker compose build ecm-frontend`
- `docker compose up -d ecm-frontend`

### Verification Steps (MCP)
1. Logged in as `admin` at `http://localhost:5500/`.
2. Opened `J0924032-02上罐体组件v2-模型.pdf` via row actions → View.
3. Inspected layout metrics with in-page script:
   - Viewport: 1280x720
   - Dialog content height: 656px
   - PDF container height: 662.5px
   - PDF canvas height: 656px
   - Dialog vs PDF container gap: -6.5px (container is slightly larger)

### Result
- PDF preview container fills the dialog content height; no extra bottom gap from layout.

### Notes
- Any remaining white space is due to the current zoom/fit mode, not the container height.
