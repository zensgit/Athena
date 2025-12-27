# UI Verification - Long Filename Display + Full Name Action (2025-12-25)

## Scope
- File list (table) long-name wrapping behavior
- File card (grid) multi-line truncation
- Context menu "Full Name" dialog + copy action

## Build/Deploy
- Rebuilt frontend image: `docker compose build ecm-frontend`
- Restarted frontend: `docker compose up -d ecm-frontend`

## Verification Steps (MCP)
1. Opened `http://localhost:5500/browse/f5801c2f-3f66-4dc2-a86e-81b9e41fbf63`.
2. List view:
   - Confirmed name column uses `white-space: normal` and auto row height.
   - Created `long-name-wrap-test-abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ`.
   - Verified the new row wraps to 2 lines without overflow.
3. Grid view:
   - Verified `J0924032-02上罐体组件v2-模型.pdf` renders as 3 lines with line clamp.
4. Context menu:
   - Opened actions for `J0924032-02上罐体组件v2-模型.pdf`.
   - "Full Name" menu item present.
   - Dialog shows full name and Copy button; copy action executes without error.

## Results
- List view: wrapping enabled; long-name row wraps to 2 lines.
- Grid view: long name renders across 3 lines (lineClamp=3).
- "Full Name" dialog: present, shows full name, copy works.
