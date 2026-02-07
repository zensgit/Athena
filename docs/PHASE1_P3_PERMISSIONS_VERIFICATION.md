# Permissions P3 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`

## Manual Verification Checklist
1. Open a document with inherited permissions.
2. In the Permissions dialog, verify inherited permissions are shown with reduced opacity and tooltip `Inherited`.
3. If a permission has both explicit and inherited entries, verify tooltip shows `Mixed explicit/inherited`.
