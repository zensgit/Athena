# Phase 6 P1 Mail Automation Preview Dialog - Development

## Summary
Improve the rule preview dialog with skip reason formatting, processable filtering, and JSON copy support.

## Scope
- Sort and format skip reason chips for readability.
- Filter matched messages by processable state.
- Allow copying the preview JSON payload.

## Implementation
- Added a `previewProcessableFilter` state and a filtered matches list.
- Sorted skip reasons by count and applied human-readable labels.
- Added a "Copy JSON" action using the clipboard API.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
