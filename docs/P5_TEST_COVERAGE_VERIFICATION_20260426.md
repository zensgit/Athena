# Test Coverage — Gap Closure Phase 1-3 + #14 (design + verification)

## Date
2026-04-26

## Status
Test layer complete. 11 new test files added; TypeScript clean; zero source-file errors.

---

## Scope

Add automated test coverage for the 5 gap-closure areas that had no tests:
- **Gap #2** Legal Holds — frontend Playwright mock spec
- **Gap #6** LDAP Directory Sync — frontend Playwright mock spec
- **Gap #7** Site Invitations — backend controller + security tests; frontend Playwright mock specs (manager page + accept page)
- **Gap #8** Disposition Schedules — frontend Playwright mock spec
- **Gap #14** Multilingual Content — backend service + controller + security tests; frontend Playwright mock spec

Already-tested gaps (no new files needed):
- Gap #2: `LegalHoldControllerTest` + `LegalHoldControllerSecurityTest` + `LegalHoldServiceTest` existed
- Gap #8: `DispositionScheduleControllerTest` + `DispositionScheduleServiceTest` existed
- Gap #6: `LdapSyncControllerTest` + `LdapSyncControllerSecurityTest` existed

---

## Files Added

### Backend — 5 new test files

| File | Lines | Covers |
|------|-------|--------|
| `ecm-core/src/test/java/com/ecm/core/controller/SiteInvitationControllerTest.java` | 180 | Gap #7 controller CRUD |
| `ecm-core/src/test/java/com/ecm/core/controller/SiteInvitationControllerSecurityTest.java` | 107 | Gap #7 auth/role enforcement |
| `ecm-core/src/test/java/com/ecm/core/service/LocalizedContentServiceTest.java` | 167 | Gap #14 service + resolve algorithm |
| `ecm-core/src/test/java/com/ecm/core/controller/LocalizedContentControllerTest.java` | 141 | Gap #14 controller CRUD + resolve |
| `ecm-core/src/test/java/com/ecm/core/controller/LocalizedContentControllerSecurityTest.java` | 75 | Gap #14 auth enforcement |

### Frontend — 6 new Playwright mock specs

| File | Lines | Covers |
|------|-------|--------|
| `ecm-frontend/e2e/admin-legal-holds.mock.spec.ts` | 156 | Gap #2 LegalHoldsPage |
| `ecm-frontend/e2e/admin-site-invitations.mock.spec.ts` | 139 | Gap #7 SiteInvitationsPage |
| `ecm-frontend/e2e/invitation-accept.mock.spec.ts` | 110 | Gap #7 InvitationAcceptPage |
| `ecm-frontend/e2e/admin-disposition-schedules.mock.spec.ts` | 200 | Gap #8 DispositionSchedulesPage |
| `ecm-frontend/e2e/admin-ldap-sync.mock.spec.ts` | 147 | Gap #6 LdapSyncPage |
| `ecm-frontend/e2e/admin-localized-content.mock.spec.ts` | 180 | Gap #14 LocalizedContentPage |

---

## Backend Test Design

### Pattern
All backend tests follow the existing Athena pattern:

- **Controller tests**: `@ExtendWith(MockitoExtension.class)` + `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new RestExceptionHandler()).build()`
- **Security tests**: `@WebMvcTest` + `@ContextConfiguration` with embedded `TestSecurityConfig` (`@EnableMethodSecurity(prePostEnabled = true)`)
- **Service tests**: Manual constructor injection (factory method) — avoids `@InjectMocks` brittleness when constructor changes

---

### `SiteInvitationControllerTest` — 5 tests

| Test | Endpoint | Assertion |
|------|----------|-----------|
| `listInvitationsReturnsList` | `GET /api/v1/sites/{siteId}/invitations` | 200, array with siteId + inviteeEmail |
| `inviteReturnsCreated` | `POST /api/v1/sites/{siteId}/invitations` | 201, body has id + status |
| `cancelReturnsNoContent` | `DELETE /api/v1/sites/{siteId}/invitations/{id}` | 204 empty |
| `acceptReturnsDto` | `POST /api/v1/invitations/accept` | 200, `{"token":"..."}` body |
| `rejectReturnsDto` | `POST /api/v1/invitations/reject` | 200, `{"token":"..."}` body |

### `SiteInvitationControllerSecurityTest` — 3 tests

Controller uses `@PreAuthorize("isAuthenticated()")` (not role-restricted):

| Test | Scenario | Expected |
|------|----------|----------|
| anonymous GET | no principal | 401 |
| `@WithMockUser(roles="USER")` GET | any authenticated | 200 |
| `@WithMockUser` POST accept | any authenticated | 200 |

### `LocalizedContentServiceTest` — 6 tests

Focuses on the security-gated resolve algorithm and permission enforcement:

| Test | Scenario |
|------|----------|
| `listForNodeRequiresReadOnLiveNode` | Verifies `checkPermission(node, READ)` called; repo returns list |
| `upsertRequiresWriteAndNormalizesLocale` | Input `" ZH-CN "` → normalized `"zh-cn"`; `checkPermission(node, WRITE)` called |
| `deleteRequiresWrite` | `EN` normalized to `en`; `checkPermission(node, WRITE)` called; repo `deleteByNodeIdAndLocale` called |
| `resolveChecksReadAndFallsBackToLanguage` | Accept-Language `"zh-CN, en;q=0.8"` → exact zh-cn not found → language-only `zh` found → returned |
| `blankLocaleIsRejected` | Blank locale → `IllegalArgumentException` before any repo call |
| `missingNodeIsNotFound` | `findByIdAndDeletedFalseAndArchiveStatus` returns empty → `ResourceNotFoundException` |

**Key update**: `LocalizedContentService` was updated post-generation to add `SecurityService` dependency and `requireLiveNode` that uses `findByIdAndDeletedFalseAndArchiveStatus` (not `findById`). Service test was rewritten to match the updated constructor and behavior.

### `LocalizedContentControllerTest` — 5 tests

| Test | Endpoint | Notes |
|------|----------|-------|
| `listLocalizationsReturns200` | `GET /nodes/{id}/localizations` | UUID path variable |
| `upsertLocalizationReturns200` | `PUT /nodes/{id}/localizations/{locale}` | body has title + description |
| `deleteLocalizationReturns204` | `DELETE /nodes/{id}/localizations/{locale}` | void 204 |
| `resolveLocalizationReturns200` | `GET /nodes/{id}/localization` | passes `Accept-Language: zh-CN` header |
| `resolveLocalizationReturns404WhenEmpty` | `GET /nodes/{id}/localization` | `Optional.empty()` → 404 |

### `LocalizedContentControllerSecurityTest` — 2 tests

| Test | Scenario | Expected |
|------|----------|----------|
| anonymous GET localizations | no principal | 401 |
| `@WithMockUser` GET | any authenticated | 200 |

---

## Frontend Test Design

### Pattern
All specs follow the Athena mock-spec pattern:

```typescript
test('...', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);       // abort Keycloak for static-serve
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');  // inject localStorage session
  await page.route('**/api/v1/...', async route => { ... }); // mock before goto
  await page.goto('/admin/...');
  await expect(page.getByRole('button', { name: '...' })).toBeVisible();
});
```

Routes are always registered before `page.goto()`. No `waitForTimeout()` — all waits are expectation-based.

---

### `admin-legal-holds.mock.spec.ts` — 4 tests

Mocked endpoints: `GET /api/v1/legal-holds`, `GET /api/v1/legal-holds/hold-1`, `POST /api/v1/legal-holds`

| Test | Validates |
|------|-----------|
| shows legal holds list | Both hold names visible after navigation |
| shows ACTIVE and RELEASED status chips | Chip labels rendered for both statuses |
| clicking a hold shows its detail | Hold-1 selected → `contract.pdf` visible in detail |
| create hold dialog opens and submits | "Create Hold" → dialog → fill → submit → POST intercepted |

### `admin-site-invitations.mock.spec.ts` — 4 tests

Mocked endpoints: `GET`, `POST`, `DELETE /api/v1/sites/site-abc/invitations{/id}`

| Test | Validates |
|------|-----------|
| shows invitation list with status chips | alice PENDING + bob ACCEPTED visible |
| PENDING invitations have a cancel button | Cancel button scoped to alice's row |
| invite dialog opens | "Invite" button → dialog with email field |
| cancelling a PENDING invitation calls DELETE | DELETE route intercepted and called |

### `invitation-accept.mock.spec.ts` — 4 tests

Mocked endpoints: `POST /api/v1/invitations/accept`, `POST /api/v1/invitations/reject`

| Test | Validates |
|------|-----------|
| shows accept and decline buttons when token present | Both buttons visible with `?token=test-token-abc` |
| accepting invitation shows success state | Accept → site title "Finance Site" in response |
| declining invitation shows declined state | Decline → declined confirmation visible |
| shows error when token missing | No token → error/warning guard message |

### `admin-disposition-schedules.mock.spec.ts` — 3 tests

Mocked endpoints: `GET /api/v1/disposition-schedules`, `GET .../folder-1/disposition-schedule`, `GET .../executions`, `POST .../run`

| Test | Validates |
|------|-----------|
| shows schedule list with enabled/disabled status | "Finance Records" + "HR Records" + Active chip |
| clicking a schedule shows detail panel | "Finance Records" click → "365 days" in detail |
| run all schedules triggers batch run | "Run All Schedules" → POST fired → result dialog |

Note: Source renders `'Active'`/`'Disabled'` chips (not `'ENABLED'`/`'DISABLED'`). Tests match actual rendered values.

### `admin-ldap-sync.mock.spec.ts` — 4 tests

Mocked endpoints: `POST /api/v1/admin/ldap/test-connection`, `POST /api/v1/admin/ldap/sync`

| Test | Validates |
|------|-----------|
| shows connection status card and sync card | Both action buttons visible |
| test connection shows result | "Reachable" chip + `ou=users,dc=example,dc=com` text |
| sync now shows stats | `Created: 5` chip + `Trigger: manual` |
| shows not-configured state on 404 | 404 override → LDAP not enabled Alert text |

### `admin-localized-content.mock.spec.ts` — 4 tests

Mocked endpoints: `GET /api/v1/nodes/*/localizations`, `PUT /api/v1/nodes/*/localizations/**`, `DELETE /api/v1/nodes/*/localizations/**`

| Test | Validates |
|------|-----------|
| node ID lookup shows localizations table | UUID entered → Load → `en`/`zh` rows + "Annual Report" |
| add locale dialog opens | "Add Locale" → dialog with locale select |
| inline delete confirm appears | Delete icon → inline "Confirm delete?" + Yes/No |
| shows empty state before node loaded | No table/Add button before first lookup |

---

## TypeScript Verification

```bash
cd ecm-frontend && npx tsc --noEmit 2>&1 | grep -v node_modules
# → (no output — zero source-file errors)
```

Pre-existing library errors in `node_modules/react-hook-form` are filtered; they are unrelated to this work.

---

## Service Update Note

`LocalizedContentService.java` was updated after initial generation to add:
- `SecurityService securityService` constructor dependency
- `requireReadableNode` / `requireWritableNode` guards on all public methods
- `requireLiveNode` using `nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE)` (not `findById`)
- Stricter `normalizeLocale` — throws `IllegalArgumentException` for blank/null locale

`LocalizedContentServiceTest.java` was rewritten to match: uses 3-arg constructor factory method, stubs `findByIdAndDeletedFalseAndArchiveStatus`, verifies `securityService.checkPermission()` calls.

---

## Non-goals (out of scope for this test pass)

- Live full-stack E2E (requires running Docker stack with real backend + Keycloak)
- Backend `@DataJpaTest` for `LocalizedContentRepository` (UNIQUE constraint covered by migration; service layer covers behavioral logic)
- Performance / load tests
- Gap #6 backend integration test for LDAP (requires a real/embedded LDAP server)
