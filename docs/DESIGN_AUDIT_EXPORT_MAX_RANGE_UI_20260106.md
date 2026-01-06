# Design: Audit Export Max Range UI (2026-01-06)

## Goal
- Surface max export range guidance and prevent invalid audit export ranges in the admin UI.

## Approach
- Expose `exportMaxRangeDays` in the existing retention info response.
- Use the value in the frontend to show helper text and block invalid ranges (empty, reversed, or over limit).

## Files
- ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java
- ecm-frontend/src/pages/AdminDashboard.tsx
