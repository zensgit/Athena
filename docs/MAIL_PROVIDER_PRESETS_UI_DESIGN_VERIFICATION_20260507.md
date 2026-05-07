# Mail Provider Presets UI — Design & Verification (2026-05-07)

## Context

Athena administrators frequently provision Mail Automation accounts for the
same handful of Chinese enterprise mail providers (Aliyun, Tencent, 263).
Today they must remember and re-type IMAP host, port, and security values for
each provider. This change adds a "Provider preset" dropdown to the Mail
Automation account form that fills those three fields from a backend-curated
catalog.

Backend contract is **fixed and consumed in parallel** (Package A is
implementing the matching `GET /api/v1/mail/automation/provider-presets`
endpoint). The frontend treats the backend as the single source of truth —
the page does not hard-code preset values.

```ts
type MailProviderPresetId =
  | 'ALIYUN_QIYE'
  | 'TENCENT_EXMAIL'
  | 'TENCENT_EXMAIL_OVERSEAS'
  | 'MAIL_263'
  | 'MAIL_263_OVERSEAS';

interface MailProviderPreset {
  id: MailProviderPresetId;
  label: string;          // e.g. "阿里云企业邮箱"
  imapHost: string;
  imapPort: number;
  imapSecurity: 'SSL' | 'STARTTLS' | 'NONE';
}
```

The response NEVER includes credentials.

## Design

### Service layer

`MailAutomationService` gains:

- `MailProviderPresetId` and `MailProviderPreset` TypeScript types matching
  the backend contract verbatim.
- `listProviderPresets(): Promise<MailProviderPreset[]>` calling
  `api.get('/mail/automation/provider-presets')`. The shared `api`
  instance prepends the `/api/v1` base URL, producing the brief-mandated
  absolute path `/api/v1/mail/automation/provider-presets`. Note: existing
  `MailAutomationService` methods use the `/integration/mail/...` prefix
  because the legacy `MailAutomationController` is mounted there. The
  preset endpoint deliberately follows the brief (Package A's contract)
  rather than the legacy prefix.

### State

`MailAutomationPage` adds two pieces of component state:

- `providerPresets: MailProviderPreset[]` — populated once on mount via a
  `useEffect`. On rejection the slot stays an empty array; the page remains
  fully functional and the dropdown shows only "Custom".
- `selectedProviderPresetId: string` — which entry (if any) is selected. The
  empty string means "Custom" (no preset). Reset to `""` whenever the
  account dialog opens for create or edit, so the dropdown does not "stick"
  across dialog sessions.

### Dropdown placement

Inside the existing `Dialog` for create/edit account, between the **Name**
field and the **Host** field. Implemented as MUI `FormControl` + `Select`
with `MenuItem` rows, label "Provider preset". The first menu item is
`Custom` (value `""`) and is the default. The remaining items are rendered
from `providerPresets` using each entry's `label` for the visible text and
`id` for the value.

### Selection handler

`handleProviderPresetChange(presetId: string)` is the single entry point:

- If `presetId === ''` (Custom): no fields are touched. Manual edits are
  preserved exactly as the operator left them.
- Otherwise it locates the matching preset and patches `accountForm`:
  `host = preset.imapHost`, `port = preset.imapPort`,
  `security = preset.imapSecurity`. It uses the functional `setAccountForm`
  signature so concurrent updates to other form slots are not clobbered.

### Manual override semantics

The Host, Port, and Security inputs remain identical to today — the user can
type or pick a different security value after applying a preset, and that
change is final. There is no auto-revert and no listener that re-syncs
from `providerPresets`. The submit flow (`handleSaveAccount`) is unchanged:
the form sends whatever the operator finalized, regardless of preset.

### Failure handling

- Network/auth failure on the preset fetch is swallowed (logged as the empty
  array fallback). The page renders normally; the dropdown shows only
  "Custom"; the Host/Port/Security inputs work as before.
- The preset fetch is independent of the bulk `loadAll` call so a slow or
  failing preset endpoint never blocks account/rule/diagnostics loading.

## Verification

### Targeted tests

Added `ecm-frontend/src/pages/MailAutomationPage.presets.test.tsx` (sibling
of `MailAutomationPage.tsx`). No prior test file existed for this page; the
new file is **scoped to the preset dropdown only** so it stays bounded and
fast. Choice rationale: the page is ~5,200 lines and pulls in many sibling
services; a targeted file keeps the mock surface narrow.

The fixture inside the test mirrors the contract shape, but the tests assert
against the fixture, not against literal strings. This keeps the test
locked to the wiring (selecting preset X populates with preset X's values),
not to specific source-of-truth values that legitimately live on the
backend.

| Test | Assertion |
| --- | --- |
| `fetches provider presets on mount` | `mailAutomationService.listProviderPresets` called once |
| `selecting Aliyun fills IMAP host/port/security from the preset fixture` | Host input value = fixture host; port = fixture port; security text = fixture security |
| `selecting Tencent fills IMAP host/port/security from the preset fixture` | Same as above for Tencent fixture |
| `selecting 263 fills IMAP host/port/security from the preset fixture` | Same as above for 263 fixture |
| `manual edits to host after picking a preset persist (no auto-revert)` | After picking preset, typing into Host overrides the preset value and stays |
| `preset endpoint failure does not crash the page; dropdown still renders Custom` | With `listProviderPresets` rejecting, the dialog still opens, the dropdown shows "Custom", and no preset entries appear |

### Test results

```
PASS src/pages/MailAutomationPage.presets.test.tsx
  MailAutomationPage provider preset dropdown
    ✓ fetches provider presets on mount
    ✓ selecting Aliyun fills IMAP host/port/security from the preset fixture
    ✓ selecting Tencent fills IMAP host/port/security from the preset fixture
    ✓ selecting 263 fills IMAP host/port/security from the preset fixture
    ✓ manual edits to host after picking a preset persist (no auto-revert)
    ✓ preset endpoint failure does not crash the page; dropdown still renders Custom

Test Suites: 1 passed, 1 total
Tests:       6 passed, 6 total
```

### Lint

`npm run lint` — clean, no errors or warnings.

### Build

`CI=true npm run build` — `Compiled successfully.` Bundle sizes unchanged
beyond the small additions for the new component.

## Files Changed

- `ecm-frontend/src/services/mailAutomationService.ts` — added
  `MailProviderPresetId`, `MailProviderPreset`, `listProviderPresets()`.
- `ecm-frontend/src/pages/MailAutomationPage.tsx` — added preset state,
  mount fetch, selection handler, and the Provider-preset Select inside
  the account dialog (between Name and Host).
- `ecm-frontend/src/pages/MailAutomationPage.presets.test.tsx` — new
  scoped test file (no prior page test existed).

## Remaining Work

- Live integration with Package A's backend endpoint will be exercised once
  Package A merges; until then the UI degrades gracefully (Custom-only
  dropdown).
- A full-stack Playwright assertion (provision an account end-to-end with a
  preset) is out of scope for this slice and should land alongside the
  Package A backend merge.
- If product expands the preset list, no frontend change is required — the
  Select renders whatever the backend returns, in the order returned.
