# Design: MainLayout Menu Test Strengthening (2026-01-06)

## Goal
- Improve role-based UI coverage in the MainLayout menu tests.

## Approach
- Assert write actions (Upload/New Folder) are enabled for admin roles.
- Assert admin-only menu item visibility for admin and absence for viewer roles.

## Files
- ecm-frontend/src/components/layout/MainLayout.menu.test.tsx
