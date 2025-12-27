## PDF Preview Fit Mode Override Verification (2025-12-25)

### Scope
- Force override legacy `ecm_pdf_fit_mode` stored value on first mount.

### Build/Deploy
- `docker compose build ecm-frontend`
- `docker compose up -d ecm-frontend`

### Verification Steps (MCP)
1. Reloaded `http://localhost:5500/browse/root`.
2. Set localStorage to legacy values:
   - `ecm_pdf_fit_mode=screen`
   - `ecm_pdf_fit_mode_version=2024-01-01`
3. Opened `J0924032-02上罐体组件v2-模型.pdf` via row actions → View.
4. Verified toolbar shows `Fit to height (55%)`.
5. Confirmed localStorage updated:
   - `ecm_pdf_fit_mode=height`
   - `ecm_pdf_fit_mode_version=2025-12-25`

### Result
- Legacy fit mode is overridden on first preview open; storage is updated to the new version.
