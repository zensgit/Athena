# Phase 368ZE — User Preferences Hardening — Verification

> **Date**: 2026-03-31

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | PreferenceService extracted as @Service | PASS |
| 2 | getPreferences with null filter returns all | PASS |
| 3 | getPreferences with prefix filter returns subset | PASS |
| 4 | getPreferences filter returns empty when no match | PASS |
| 5 | listNamespaces returns sorted distinct prefixes | PASS |
| 6 | listNamespaces handles keys without dots | PASS |
| 7 | validateKey accepts dotted alphanumeric key | PASS |
| 8 | validateKey accepts dashes and underscores | PASS |
| 9 | validateKey rejects blank | PASS |
| 10 | validateKey rejects spaces | PASS |
| 11 | validateKey rejects leading dot | PASS |
| 12 | validateKey rejects key > 200 chars | PASS |
| 13 | validateValueSize accepts null | PASS |
| 14 | validateValueSize rejects > 10,000 chars | PASS |
| 15 | setPreference creates new preference | PASS |
| 16 | setPreference updates existing | PASS |
| 17 | setPreference rejects invalid key | PASS |
| 18 | replaceAll replaces atomically | PASS |
| 19 | replaceAll rejects batch with invalid keys | PASS |
| 20 | Non-owner non-admin rejected | PASS |
| 21 | Admin can modify other user's preferences | PASS |
| 22 | GET ?filter= param works on endpoint | PASS |
| 23 | GET /namespaces endpoint returns list | PASS |
| 24 | Frontend PeopleDirectoryPage loads filtered preferences | PASS |
| 25 | Frontend PeopleDirectoryPage loads namespace list | PASS |
| 26 | Frontend raw JSON edit is disabled in filtered mode | PASS |
| 27 | Frontend peopleService forwards filter query param | PASS |
| 28 | Frontend peopleService fetches namespaces endpoint | PASS |

## 2. Hot-File Constraint

Zero preview/rendition/search/ops-governance files modified. No DB migration.

## 3. Frontend Verification

Validated the frontend namespace/filter consumption with targeted checks:

- `cd ecm-frontend && ./node_modules/.bin/eslint src/services/peopleService.ts src/services/peopleService.test.ts src/pages/PeopleDirectoryPage.tsx`
- `cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/services/peopleService.test.ts`

## 4. Notes

The frontend keeps raw JSON editing and full-clear actions disabled while a namespace filter is active to avoid accidental overwrites of hidden preference groups.

## 5. Test Inventory

### PreferenceServiceTest.java — 20 tests

```
GetFiltered (3):
  ✓ returns all when filter is null
  ✓ filters by prefix
  ✓ returns empty map when no matches

Namespaces (2):
  ✓ returns distinct top-level prefixes sorted
  ✓ returns key itself when no dot

KeyValidation (6):
  ✓ accepts valid dotted key
  ✓ accepts alphanumeric with dashes and underscores
  ✓ rejects blank key
  ✓ rejects key with spaces
  ✓ rejects key starting with dot
  ✓ rejects key exceeding max length

ValueValidation (2):
  ✓ accepts null value
  ✓ rejects oversized value

SetPreference (3):
  ✓ creates new preference
  ✓ updates existing preference
  ✓ rejects invalid key format

ReplaceAll (2):
  ✓ replaces all preferences atomically
  ✓ rejects batch with invalid keys

Permissions (2):
  ✓ rejects non-owner non-admin writes
  ✓ allows admin to modify other user's preferences
```

## 4. Full Regression

```
Phase 368ZE (User Preferences):              20 tests ✓
Phase 368ZC-ZD (Rating / Likes):             15 tests ✓
Phase 368Y (Discovery API):                   6 tests ✓
Phase 368X (Association Operator Surface):     7 tests ✓
Phase 368W (Cross-Surface Entry):              4 tests ✓
Phase 368V (Admin Governance Surface):        10 tests ✓
Phase 368U (Operator Surface Convergence):     4 tests ✓
Phase 368T (Shared Links Enhancement):         9 tests ✓
Phase 368R (Node Associations):               10 tests ✓
Phase 368Q (Type Enforcement):                14 tests ✓
Phase 368O (Request Contract):                11 tests ✓
Phase 368M (Aspect Property Enforcement):     13 tests ✓
Phase 368K (Content Model Authoring):         53 tests ✓
Phase 361-365 (Content Model + Aspect):        6 tests ✓
Phase 364B (Lock Enhancement):                38 tests ✓
Phase 368A (Working Copy):                    54 tests ✓
Existing tests:                               21 tests ✓
────────────────────────────────────────────────────────
Total:                                       295 tests, 0 failures
BUILD SUCCESS
```
