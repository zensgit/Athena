# Collabora Welcome Dialog Suppression Verification

Date: 2025-12-26

## Change
- Enabled `home_mode` in Collabora by setting:
  `extra_params=--o:ssl.enable=false --o:ssl.termination=false --o:home_mode.enable=true`

## Steps
1. Recreated Collabora container.
2. Opened `工作簿1.xlsx` via context menu → "Edit Online".
3. Checked for welcome dialog iframe.

## Result
- The editor loaded without the Collabora welcome dialog.
- No iframe sources containing `welcome` were present.

## Note
- `home_mode` disables welcome/feedback popups but caps concurrency (20 connections / 10 documents) per Collabora config.
