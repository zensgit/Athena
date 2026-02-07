# Phase 1 P40: Frontend Login Stability + White-Screen Guard Design

## Date
2026-02-07

## Background

Two stability gaps remained after prior auth/search iterations:

1. No explicit smoke regression to ensure the login CTA still reaches Keycloak auth endpoint.
2. Runtime render exceptions could still result in a white screen with no recovery actions.

During verification, one additional deployment packaging issue was found:

- `ecm-frontend` prebuilt Docker image could serve default Nginx landing page because build assets were not fully copied into image root.

## Scope

- `ecm-frontend/e2e/p1-smoke.spec.ts`
- `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
- `ecm-frontend/src/components/layout/AppErrorBoundary.test.tsx`
- `ecm-frontend/src/index.tsx`
- `ecm-frontend/Dockerfile.prebuilt`
- `ecm-frontend/.dockerignore`

## Design Decisions

1. Add explicit login CTA redirect smoke coverage

- New P1 smoke test:
  - Opens `/login`
  - Clicks `Sign in with Keycloak`
  - Asserts URL enters Keycloak authorize endpoint and includes `client_id=unified-portal`.

2. Add global App error boundary

- Introduced `AppErrorBoundary` at app root wrapping `<App />`.
- On render crash, fallback page provides:
  - clear error notice
  - `Reload` action
  - `Back to Login` action
- In non-production builds, fallback includes error detail text to speed diagnosis.

3. Fix prebuilt frontend image asset copy behavior

- `Dockerfile.prebuilt` changed to copy build contents into web root:
  - from `COPY build /usr/share/nginx/html`
  - to `COPY build/ /usr/share/nginx/html/`
- `.dockerignore` updated to include build children:
  - add `!build/**`
- This ensures `index.html` and static bundles replace Nginx default files in container image.

## Expected Outcome

- Login CTA regressions are caught by CI smoke.
- Render crashes no longer manifest as unrecoverable blank page.
- Prebuilt frontend container consistently serves Athena UI instead of default Nginx page.

