# Permissions P3 — Inherited/Explicit Visibility

Date: 2026-02-06

## Context
Permissions API returns `inherited` per entry, but the UI previously collapsed permissions without showing whether each grant was inherited or explicit. This makes troubleshooting ACL inheritance harder.

## Goals
- Surface inheritance at the permission cell level in the Permissions dialog.

## Design
- Track permission origin when loading entries (`INHERITED`, `EXPLICIT`, or `MIXED`).
- Render inherited permissions with reduced opacity and tooltip `Inherited`.
- Render mixed origin permissions with tooltip `Mixed explicit/inherited`.

## Files Changed
- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`

