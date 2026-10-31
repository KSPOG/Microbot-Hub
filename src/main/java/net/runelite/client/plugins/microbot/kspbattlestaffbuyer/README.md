# KSP Battlestaff Buyer v0.0.10

## Automatic cash-driven cycle

There is no longer a user-entered Battlestaff bank threshold or sell price.

The plugin now:
1. Buys Battlestaves from Zaff while it can afford the next staff.
2. Banks when inventory is full and continues buying.
3. Calculates the next Zaff price from current shop stock.
4. When current coins can no longer afford another Battlestaff, it starts the sell cycle.
5. Banks remaining Battlestaves.
6. Withdraws only the Battlestaves acquired during the current plugin cycle as notes.
7. Walks to the Grand Exchange.
8. Calculates the actual acquisition cost from coin decreases observed during purchases.
9. Calculates a minimum profitable GE price after the 2% GE tax.
10. Uses the live GE sell-side price when it is already profitable; otherwise it lists at the protected minimum profitable price.
11. Waits for the sale to complete.
12. Collects the coins.
13. Returns to Zaff and repeats.

## Profit protection

The plugin never intentionally lists the cycle below acquisition cost after GE tax.

For safety, if exact purchase-cost history is incomplete (for example the plugin is started mid-cycle), the calculation assumes at least 7,000 gp acquisition cost per Battlestaff rather than underestimating cost.

## Other existing behavior

- Shop-widget checks before buying or hopping.
- Shop must be fully closed before world hopping.
- Fast Zaff interaction after a hop.
- Random nearby banker / bank booth selection.
- Accessible members-world hopping.
- Overlay includes cycle quantity, acquisition cost, profit floor, active GE offer, shop stock, hops, bank trips and coins.

## v0.0.7 custom sell price

Grand Exchange config now includes:
- **Use Custom Sell Price** — checkbox, disabled by default.
- **Custom Sell Price** — exact GP price per Battlestaff.

Behavior:
- Custom Sell Price = OFF: automatic live-market + profit-floor calculation is used.
- Custom Sell Price = ON: the configured Custom Sell Price is used exactly for the GE offer.

Note: custom mode intentionally overrides the automatic profit-floor calculation. If the configured custom price is below the calculated profitable floor, the plugin still uses the user's configured price because custom mode is an explicit override.

## v0.0.8 P2P-only world hopping
- World selection still requests `membersOnly = true`.
- Every selected target is now independently checked against the live Microbot world list.
- The target must explicitly contain `WorldType.MEMBERS`.
- The target must also pass `Rs2WorldUtil.canAccessWorld(...)`.
- A final members-world validation is performed immediately before `Microbot.hopToWorld(...)`.
- F2P targets are rejected rather than used as a fallback.

## v0.0.9 world-hop fix
- Removed the redundant `Rs2WorldUtil.canAccessWorld(...)` validation that could reject valid P2P targets.
- P2P enforcement still explicitly requires `WorldType.MEMBERS`.
- Hop dispatch no longer depends only on the immediate boolean from `Microbot.hopToWorld(...)`.
- A hop is accepted if the call succeeds, the client enters hopping state, or the current world changes.
- Restored immediate post-hop Zaff interaction by resetting the interaction gate instead of adding the old retry delay.

## v0.0.10 sell-all bank stock fix
- The GE sell cycle now withdraws **all Battlestaves currently in the bank** as notes.
- Removed the old limit that only withdrew `cycleAcquiredStaffs`.
- The GE offer quantity now uses the complete noted Battlestaff stack.
- Automatic profit protection remains conservative: when selling older banked Battlestaves with unknown acquisition cost, the plugin assumes at least Zaff's 7,000 gp base cost per staff for the break-even floor.
- Custom Sell Price mode still overrides automatic pricing exactly as configured.
