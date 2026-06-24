# Sensitive-Data Logging Audit — Phase 1 Taskbook (re-grounded)

Status: **TASKBOOK — for owner "go". Read-only; NO code / tests / config.** Re-grounds the 2026-05-23
design against the current code (2026-06-23).

## Method / taxonomy / deliverable — REUSE the existing design
`docs/SENSITIVE_DATA_LOGGING_AUDIT_DESIGN_20260523.md` already specifies the method (enumerate →
classify **SAFE / NEEDS-MASK / LEAKS** → positive-proof for NEEDS-MASK → indirect-leak vectors:
`toString()`, exception `super(message)`, `System.out/err`), the findings-doc output format, the OOS,
and the Phase-2 gate. **All reused unchanged** — this taskbook only re-maps the lanes.

## Why this taskbook exists — the design's lane PATHS drifted (banked "re-verify" lesson)
Baseline (2026-06-23, read-only grep): total log call-sites in `ecm-core/src/main` = **608**;
`System.out/err` in production = **0** (good). The design's L1/L2/L3 paths no longer match:

| Lane | Design path (2026-05-23) | Current reality (2026-06-23) | Log sites |
|---|---|---|---|
| L1 OAuth | `integration/oauth` (named "highest risk") | dir exists (14 files) but **only `OAuthCredentialService` carries a logger** (~0 `log.X()` by strict grep); the OAuth/credential surface also spans `controller/OAuthCredentialAdminController` + `integration/mail/service/MailOAuth*` | ~0–few (audit confirms) |
| L2 Secret/Property | `security/property` (**MISSING**) | **`security/secret`** — `SecretCryptoService`, `EncryptedSecretConverter`, `SecretCryptoProperties` | 3 files |
| L3 Transfer | `transfer` (**MISSING**) + `service/transfer` (0) | logging is in **`service/TransferReplicationService` + `service/TransferReplicationScheduler`** (the receiver itself logs 0) | 2 files |
| L4 Mail | `integration/mail` | unchanged — **the biggest lane** (incl. mail-side OAuth) | **65** |
| L5 WOPI | `integration/wopi` | unchanged | 2 |
| L6 LDAP | `integration/ldap` | unchanged | 6 |
| L7 Security (non-secret) | `security/**` | unchanged | 4 |
| L8 config / exception | `config` / `exception` | unchanged | 5 / 0 |

## Corrected scope (Phase 1, read-only)
Classify the log call-sites in the CURRENT sensitive lanes above (≈ **90–100 sites, mail-dominated**).
**Additions vs the 2026-05-23 design** (drift-driven): `controller/OAuthCredentialAdminController`,
`integration/mail/service/MailOAuth*` (mail-side OAuth), `service/TransferReplication*`. Mail (65) may
get its own pass; the rest are small enough for a single pass.

## Deliverable + gate
`docs/SENSITIVE_DATA_LOGGING_AUDIT_FINDINGS_20260623.md` per the design's output format (per-lane tables:
`File:line | Level | Format | Interpolated args | Class | Notes`, + summary counts + indirect-leak findings
+ honest gaps). **Phase 2 opens only if** LEAKS ≥ 1, OR `System.out/err` ≥ 1, OR NEEDS-MASK > 10 with an
inconclusive positive-proof sample. Otherwise the findings doc closes the track.

## Ratify (owner)
**"go"** to author the findings doc against the corrected lanes? Confirm the 3 scope additions (or trim).
No grep-into-findings until "go" (design OOS: read-only; no commits without explicit go).
