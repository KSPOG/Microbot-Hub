# 1.0.3

- Removed transient context-menu scraping for GE Abort and Modify actions.
- Abort now invokes the occupied slot's direct `Abort offer` CC_OP used by Microbot.
- Modify now supports both client layouts:
  - direct `Modify offer` slot operation when exposed;
  - `View offer` followed by the status screen's `Modify offer` control.
- Ignores stale home-screen slot overlays whenever a GE slot/status/setup screen is already open.
- Stops repeated `GE slot menu did not expose 'Modify offer'` warnings.
