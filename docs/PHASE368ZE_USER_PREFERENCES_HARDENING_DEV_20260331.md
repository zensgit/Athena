# Phase 368ZE — User Preferences Hardening

> **Scope**: Service extraction, namespace filter, key/value validation, frontend namespace consumption, consistency, focused tests
> **Date**: 2026-03-31

---

## 1. Problem Statement

Preferences were fully functional (JSONB on User, 6 endpoints, frontend editor) but:

| Gap | Risk |
|-----|------|
| All logic in PeopleController — no service layer | Untestable business rules |
| No namespace filter / namespace list surface | Users with 100+ prefs can't narrow by group |
| No key validation | Arbitrary strings (spaces, emoji, 10k-char keys) accepted |
| No value size limit | Unbounded JSONB payloads |
| No preference count limit | Potential DB bloat |
| No tests | Zero coverage on preference operations |
| Bulk replace and single upsert followed different code paths | Inconsistency risk |
| Frontend only rendered flat key/value list | No namespace grouping or filter controls |

## 2. What Was Built

### PreferenceService (new)

Extracted from PeopleController into a dedicated testable service:

| Method | Description |
|--------|-------------|
| `getPreferences(username, filter?)` | All prefs, or filtered by prefix |
| `getPreference(username, key)` | Single preference |
| `listNamespaces(username)` | Distinct top-level prefixes (sorted) |
| `setPreference(username, key, value)` | Validated upsert |
| `replaceAll(username, prefs)` | Validated bulk replace |
| `deletePreference(username, key)` | Remove single |
| `clearAll(username)` | Remove all |

### Validation Contract

| Rule | Limit |
|------|-------|
| Key format | Alphanumeric + dots/dashes/underscores, no spaces |
| Key length | Max 200 characters |
| Value size | Max 10,000 characters (toString) |
| Preference count | Max 500 per user |
| Bulk replace | Validates every key + value before save |

### New Endpoints (1 new, 1 enhanced)

| Endpoint | Change |
|----------|--------|
| `GET /{username}/preferences?filter=ui.` | Enhanced with `filter` query param |
| `GET /{username}/preferences/namespaces` | **New** — returns distinct namespace prefixes |
| `PUT /{username}/preferences/{key}` | Now delegates to PreferenceService with validation |

### Frontend Consumption

| File | Change |
|------|--------|
| `services/peopleService.ts` | Added `getPreferences(username, filter?)` and `getPreferenceNamespaces(username)` |
| `pages/PeopleDirectoryPage.tsx` | Added namespace chips, grouped preference sections, and filtered preference loading |
| `pages/PeopleDirectoryPage.tsx` | Disabled raw JSON editing in filtered mode to avoid overwriting hidden namespaces |
| `services/peopleService.test.ts` | Added focused coverage for namespace filter and namespace listing requests |

## 3. Files Created

| File | Purpose |
|------|---------|
| `service/PreferenceService.java` | Extracted service with validation + namespace filter |
| `test/service/PreferenceServiceTest.java` | 20 focused tests |

## 4. Files Modified

| File | Change |
|------|--------|
| `controller/PeopleController.java` | +PreferenceService injection; +filter param on GET; +namespaces endpoint; upsert delegates to service |

## 5. NOT Modified

All preview/rendition/search/ops-governance files untouched. No DB migration needed (JSONB column + GIN index already exist).
