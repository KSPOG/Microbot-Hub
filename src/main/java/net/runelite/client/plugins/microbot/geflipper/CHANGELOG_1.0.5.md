# GE Flipper 1.0.5

- Reworked Abort/Modify into a strict two-phase context-menu interaction.
- Closes any stale menu before beginning a slot action.
- Right-clicks the full current GE slot root rather than a nested highlighted child.
- Waits until RuneLite reports the menu as open, with positive geometry and the exact target entry.
- Requires the same menu geometry and matching entry for three consecutive polls before continuing.
- Physically clicks the visible Abort offer / Modify offer menu row only after the menu is stable.
- Validates that the menu entry belongs to the exact resolved slot widget ID.
- Cancels safely with Escape when the menu does not open, is unstable, or lacks the requested action.
- Removed the direct menu invocation path for Abort/Modify.
