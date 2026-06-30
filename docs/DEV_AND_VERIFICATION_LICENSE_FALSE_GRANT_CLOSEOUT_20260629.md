# License placeholder false-grant closeout — Development & Verification (2026-06-29)

## 1. Why this slice exists

`LicenseService` was already documented as a display-only placeholder, but its runtime behavior still
overstated capability: any non-empty `ecm.license.key` except the literal `invalid` produced a hardcoded
Enterprise license (`WORKFLOW`, `OCR`, `AUDIT`, 100 users, 1000 GB, +1 year). That made the AdminDashboard
License card look commercially enabled even though no JWT/RSA verification or enforcement path exists.

This slice removes that false-grant behavior. It does **not** implement real licensing.

## 2. What changed

- `LicenseService.validateLicense()` no longer fabricates Enterprise data.
- Empty key remains Community `valid=true`.
- Any configured key falls back to Community `valid=false` until signed-license verification exists.
- Community feature behavior stays unchanged: `BASIC` remains enabled; commercial feature names such as
  `WORKFLOW` / `OCR` remain disabled.
- Existing `checkUserLimit()` and `isFeatureEnabled()` enforcement hooks remain non-wired placeholders.

## 3. Boundaries

- No JWT/RSA parsing.
- No license server.
- No create-user or feature-route enforcement.
- No frontend redesign. The existing AdminDashboard License card now receives a truthful backend status
  instead of a hardcoded Enterprise mock.

## 4. Verification

Targeted unit coverage:

- blank key -> Community, `valid=true`, 5 users, 10 GB, `[BASIC]`;
- arbitrary configured key -> Community, `valid=false`, no Enterprise feature grant;
- literal `invalid` -> Community, `valid=false`.

Command used:

```bash
cd ecm-core
./mvnw -Dtest=LicenseServiceTest,LicenseControllerSecurityTest test
```

## 5. Remaining product decisions

Real licensing remains a separate product line: edition model, billable-seat definition, signed-license
source of truth, expiry/grace behavior, and actual enforcement wiring all still need owner decisions before
runtime enforcement should be built.
