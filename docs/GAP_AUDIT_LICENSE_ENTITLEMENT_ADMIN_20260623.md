# License/Entitlement Admin — Gap Audit

Status: **GAP AUDIT — for owner ratify. NO code yet.** Read-only audit 2026-06-23.
Framing: NOT "build a missing page" — `/api/v1/system/license` + an AdminDashboard License panel already
exist. **The audit's primary finding (§2) reframes the work — read it before §4.**

## 0. DECISION — RATIFIED C+ (owner, 2026-06-23)

This doc stays as the **decision record**. Ratified outcome:
- Keep this gap audit; **correct the misleading `docs/FEATURE_LICENSING.md`** (it claimed enforcement is wired — see §2.4) to a "placeholder / not enforced yet" status.
- Do **NOT** polish the License admin UI, remove the dead methods, or start real licensing — the subsystem enforces nothing (§2), so polish has near-zero value.
- **Next mainline = Sensitive-Data Logging Audit Phase 1** (genuinely live + high-value).
- Minimal honest loop = a **doc-only PR** (this gap audit + the `FEATURE_LICENSING.md` correction); after merge, open the logging-audit taskbook. (= §4 path "(C)" plus the existing-doc fix in §2.4.)

## 1. What exists today (precise, code-grounded)

- Backend `LicenseService` + `LicenseController` (`GET /api/v1/system/license`, `hasRole('ADMIN')`).
  `LicenseInfo` = `edition, maxUsers:int, maxStorageGb:long, expirationDate:Date?, features:String[], valid:boolean`.
- Frontend `AdminDashboard.tsx` License panel (L3503-3550): renders the fields as chips; `LicenseInfo` TS type (L147) matches the backend; fetch `.catch(()=>null)` → "Unavailable".
- Tests: only `LicenseControllerSecurityTest` (security). No shape/contract test.

## 2. ⚠️ PRIMARY FINDING — the license subsystem is a non-enforcing, mock, display-only placeholder

Three code-confirmed facts:

1. **Validation is mocked.** `validateLicense()` (L56-84) is explicitly "MVP/Demo… Placeholder logic":
   any non-blank/non-`"invalid"` key → **hardcoded Enterprise** (100 users / 1000 GB / +1yr / [WORKFLOW,OCR,AUDIT]);
   empty → Community; `"invalid"` → Community fallback. No JWT/RSA parsing (imports present, unused).
   **`valid` is always `true`** on every path — the Valid/Invalid chip is decorative.
2. **Enforcement is DEAD CODE.** Both enforcement methods have **ZERO callers in the whole repo**:
   - `isFeatureEnabled(feature)` — defined (L89), never called → **no feature is license-gated**;
   - `checkUserLimit()` — defined (L104), never called → **the `maxUsers` limit enforces nothing**.
3. **Single display-only consumer.** `LicenseService` is injected only by `LicenseController` (returns the DTO);
   `/system/license` is consumed only by `AdminDashboard.tsx` (read-only). No feature enum/registry — feature
   names are bare string literals inside `LicenseService`.
4. **An existing doc actively MISCLAIMS enforcement (the most harmful artifact).** `docs/FEATURE_LICENSING.md`
   (2025-12-10, status "✅ 已完成") stated `checkUserLimit()` "is called on user creation" and `isFeatureEnabled()`
   "is used in code for feature toggles" — both false (dead code, fact 2). A doc claiming working enforcement is
   worse than no doc — it misleads the next developer. **Corrected in this PR** to "placeholder / not enforced yet".

→ **The license subsystem enforces nothing. It is mock data rendered in one admin panel.** That is the
deliverable of this audit: before any admin polish, know that nothing is actually licensed or limited.

## 3. The 5 review points — re-scored against §2

| # | Point | Value GIVEN the placeholder finding |
|---|---|---|
| 1 | Expiry severity | near-zero — adds severity to a hardcoded `+1yr` date |
| 2 | Usage vs limit | near-zero — renders e.g. "3 / 5" against a limit that enforces nothing |
| 3 | Feature display vs `isFeatureEnabled` | **cosmetic** — `isFeatureEnabled` is dead, so there is no *functional* inconsistency, just two unused representations |
| 4 | Shape test | low — pins a display-only contract |
| 5 | Docs (editions / invalid / status) | **HIGH — and worse than "none":** `FEATURE_LICENSING.md` exists and *misclaims* enforcement (§2.4); correcting it to "placeholder / not enforced" is the P1 doc action (done in this PR) |

## 4. Recommendation — the audit changes the call: do NOT default to a polish loop

Polishing #1-#4 decorates a façade that enforces nothing. Three honest directions:

- **(A) Document + dead-code hygiene (small, closeable).** Correct the license-status doc (`FEATURE_LICENSING.md`,
  done in this PR); and either mark/remove the two dead methods OR add a "not enforced yet" note to the admin
  panel so the UI doesn't imply enforcement. ~0.5 day. (This PR took the doc-correction half only — see §0.)
- **(B) Make licensing REAL (separate, deliberate product line — NOT a small loop).** Real key verification
  (JWT/RSA) + WIRE `checkUserLimit`/`isFeatureEnabled` into the create-user / feature paths + a feature registry.
  Only if licensing is a genuine product requirement. This is a product-semantic decision, not a starter loop.
- **(C) Document (A's doc) + move the small-loop starter to the next candidate.** Take the **Sensitive-Data
  Logging Audit (your #2 priority)** — which is genuinely *live* and high-value — as the first small loop instead.

**My lean: (C).** The gap audit did its job — it found the License Admin "enhancement" would polish a
placeholder. The honest move is a short status doc (A's doc), then start the real small loop on the logging
audit. "Make licensing real" (B) is a separate product decision to schedule on its own, not bootstrap here.

## 5. Next step (C+ ratified — see §0)

After this doc-only PR merges: open the **Sensitive-Data Logging Audit Phase 1** taskbook (read-only
classification) as the new small-loop starter. **Real licensing remains a separate product decision** (§4 (B)),
not bootstrapped here.
