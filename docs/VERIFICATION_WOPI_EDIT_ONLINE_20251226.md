# WOPI Edit Online Verification

Date: 2025-12-26

## Scenario
- From `http://localhost:5500/browse/root`, opened context menu for `工作簿1.xlsx` and selected "Edit Online".

## Result
- Navigated to `/editor/<nodeId>?provider=wopi&permission=write`.
- Collabora Online loaded inside the "Online Editor" iframe (toolbar and sheets visible).
- Welcome dialog from Collabora appeared, indicating the editor is fully loaded.

## Notes
- The welcome dialog is a Collabora Online Development Edition prompt; it can be dismissed with the "Close" button inside the iframe.
