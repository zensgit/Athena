# Phase C Step 2 - Share Link Access

## Goal
Allow unauthenticated users to access share links (VIEW/COMMENT/EDIT) via `/api/v1/share/access/{token}` without tripping the global API authentication guard.

## Approach
1. Update `SecurityConfig` to permit `/api/v1/share/access/**` and the legacy `/api/share/access/**` routes before the authenticated `/api/**` matcher.
2. Rebuild the `ecm-core` Docker image and restart the container so the updated configuration is active in the running stack.

## Implementation Notes
- `docker compose build ecm-core` rebuilds the Spring Boot image using the latest source tree.
- `docker compose up -d ecm-core` recreates the container; a short delay (≈5s) is required before running verification while the service warms up.
