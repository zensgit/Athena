# Mail Automation P32 — Verification

Date: 2026-02-06

## Automated Validation
- `cd ecm-frontend && npm run lint`
  - Result: pass
- `cd ecm-frontend && npx playwright test e2e/mail-automation.spec.ts --reporter=line`
  - Result: blocked by runtime environment limits.
  - Initial error: missing executable at expected `mac-x64` shell path.
- `cd ecm-frontend && PLAYWRIGHT_BROWSERS_PATH=/tmp/pw-browsers-x64 PLAYWRIGHT_HOST_PLATFORM_OVERRIDE=mac15 ./node_modules/.bin/playwright install chromium chromium-headless-shell`
  - Failed: browser package download DNS resolution failed (`ENOTFOUND cdn.playwright.dev`, `ENOTFOUND playwright.download.prss.microsoft.com`).
- `cd ecm-frontend && PLAYWRIGHT_HOST_PLATFORM_OVERRIDE=mac15-arm64 npx playwright test e2e/mail-automation.spec.ts --reporter=line`
  - Browser launches from existing arm64 cache path, but Chromium exits immediately with permission failure:
    - `FATAL ... MachPortRendezvousServer ... Permission denied (1100)`.
- `cd ecm-frontend && PLAYWRIGHT_HOST_PLATFORM_OVERRIDE=mac15-arm64 npx playwright test e2e/mail-automation.spec.ts -g "Mail automation runtime health panel renders" --headed --reporter=line`
  - Same class of permission failure (`crashpad bootstrap_check_in ... Operation not permitted`).

## Covered Tests Present in Spec
- `Mail automation can replay failed processed item`
- `Mail automation runtime health panel renders`
- `Mail automation diagnostics export scope snapshot renders`
- `Mail automation mail-documents similar action navigates to search`

## Manual Fallback Checklist
1. Open `/admin/mail`.
2. Confirm runtime health panel is visible and refresh works.
3. Confirm export scope snapshot text appears before CSV export.
4. From mail documents table, click "find similar documents" and confirm search page navigation.
5. Trigger replay action for one processed row and verify user feedback.
