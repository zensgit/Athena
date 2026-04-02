# Phase 368ZQ — Site Membership Backend Convergence

> **Scope**: Extract membership into SiteMembershipService, add site-centric endpoints to SiteController, rewire SitesPage
> **Date**: 2026-04-01

---

## 1. Problem Statement

Membership requests lived in PeopleController (`/api/v1/people/{username}/site-membership-requests`),
manipulating User.preferences JSONB directly in controller code. This was:

- **User-centric**: Had to know the username to query/moderate requests
- **No service layer**: Business logic mixed into controller
- **Not site-centric**: Alfresco's model is `/sites/{siteId}/membership-requests`

## 2. What Was Built

### SiteMembershipService (new)

Extracted from PeopleController JSONB manipulation into a dedicated, testable service:

| Method | Description |
|--------|-------------|
| `getRequestsForSite(siteId)` | All requests for a site (aggregates across users) |
| `getRequestsForUser(username)` | All requests for a user |
| `createRequest(siteId, request)` | Create pending request (current user) |
| `approve(siteId, username, comment)` | Admin approves (sets APPROVED + decision metadata) |
| `reject(siteId, username, comment)` | Admin rejects |
| `withdraw(siteId)` | Current user withdraws own request |

### SiteController — New Membership Endpoints (5)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/sites/{siteId}/membership-requests` | List requests for a site |
| POST | `/sites/{siteId}/membership-requests` | Request membership |
| POST | `/sites/{siteId}/membership-requests/{username}/approve` | Approve (admin) |
| POST | `/sites/{siteId}/membership-requests/{username}/reject` | Reject (admin) |
| DELETE | `/sites/{siteId}/membership-requests` | Withdraw own request |

### Frontend Convergence

- `siteService.ts` — 5 new membership methods consuming `/sites/...` contract
- `SitesPage.tsx` — rewired from `peopleService` to `siteService` for all membership operations
- Request panel shows `username` for admin context
- Approve/reject uses `req.username` (from site-centric response) not `effectiveUser.username`

## 3. Files Created

| File | Purpose |
|------|---------|
| `service/SiteMembershipService.java` | Extracted membership logic + DTOs |
| `test/service/SiteMembershipServiceTest.java` | 8 focused tests |

## 4. Files Modified

| File | Change |
|------|--------|
| `controller/SiteController.java` | +SiteMembershipService injection, +5 membership endpoints |
| `services/siteService.ts` | +5 membership methods + MembershipRequestDto + CreateMembershipRequest types |
| `pages/SitesPage.tsx` | Rewired from peopleService to siteService for all membership operations |
| `test/.../SiteMembershipContractTest.java` | +membershipService mock for constructor |
| `test/.../SiteControllerTest.java` | +membershipService mock for constructor |

## 5. NOT Modified

PeopleController endpoints remain for backward compatibility. All preview/rendition/search/ops-governance files untouched.

## 6. API Migration Path

| Operation | Old (PeopleController) | New (SiteController) |
|-----------|----------------------|---------------------|
| List requests for site | GET `/people/site-membership-requests?siteId=X` | GET `/sites/{siteId}/membership-requests` |
| Create request | POST `/people/{me}/site-membership-requests` | POST `/sites/{siteId}/membership-requests` |
| Approve | POST `/people/{user}/site-membership-requests/{site}/approve` | POST `/sites/{siteId}/membership-requests/{user}/approve` |
| Reject | POST `/people/{user}/site-membership-requests/{site}/reject` | POST `/sites/{siteId}/membership-requests/{user}/reject` |
| Withdraw | DELETE `/people/{me}/site-membership-requests/{site}` | DELETE `/sites/{siteId}/membership-requests` |
