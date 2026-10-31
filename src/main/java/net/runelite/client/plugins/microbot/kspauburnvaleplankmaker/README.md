# Fast Planker v0.4.3 — Private Microbot version

This is the Microbot/source-loader version. It is separate from the SlugBot port and is not published to Plugin Releases.

## v0.4.3

- Fixed `Offer wait (sec)` enforcement for sell offers. The timer is now tied to the live GE slot/price/quantity instead of a loose item-id timestamp.
- Partial fills, a changed offer price, or a newly observed offer reset the complete configured sell wait before another Modify action is allowed.
- Sell repricing continues to use the real GE `Modify offer` path; it does not cancel the offer.

- Fixed standalone compilation: Fast Planker no longer imports `net.runelite.client.plugins.microbot.kspsupport.*`.
- The five Break Handler settings are declared directly by Fast Planker and the plugin now contains its own break service with the same KSP pause/logout/login ownership semantics.
- `KspAuburnvalePlankMakerScript` now extends Microbot `Script` directly and respects the local break service before running each action tick.
- World Hopper now checks the local break service, so hopping cannot race a Fast Planker break.

## v0.4.1

- Added the KSP **Break Handler** integration used by the current KSP automation plugins. The Fast Planker config now exposes `Do Breaks`, randomized `Break After Min/Max`, and randomized `Break Duration Min/Max` settings.
- Breaks use the shared KSP break service, which owns its pause safely, waits for safe logout conditions, keeps the account offline for the selected duration, then returns to the previous world.
- Added **Custom World Hopper** with `X Minutes` and `Y Minutes`. Each hop interval is randomized inclusively between the configured values; reversed values are normalized automatically.
- Custom hopping selects **members worlds only** and rejects PvP/high-risk/bounty/LMS and unsupported special-mode worlds.
- Total-level members worlds are allowed only when the account's current total level satisfies the requirement parsed from the world activity.
- Offline and near-full worlds are skipped, the current world is excluded, and failed hops retry without selecting a new target every scheduler tick.
- World hopping defers while an interaction/animation/dialogue is active, and safely closes bank/Grand Exchange interfaces before hopping.
- Fixed slow GE sell repricing to use the game's real **Modify offer** flow. Existing offers are no longer aborted, collected, and recreated merely to change price.
- Added a short post-modify guard so the transient GE offer-array update cannot be mistaken for an empty slot and create a duplicate sell offer.
- Version bumped to `0.4.1`.

## v0.4.0 behavior retained

- Persistent plank liquidation: once `Sell planks at` is reached, **all** remaining configured planks must be sold before logs can be bought.
- Live GE offer management and completed-offer collection checks.
- Slow sell offers start at reference price and reprice downward by about 1% per `Offer wait` interval until the configured sell floor is reached. At the floor the offer stays active.
- Completed sell offers call `collectAllToBank()` and do not leave `SELL_OUTPUTS` until the corresponding GE slot is actually cleared.
- Buy offers are placed at the configured buy ceiling and remain active on timeout instead of being repeatedly cancelled/recreated.
- Every buy is collected and its GE slot must clear before the next buy starts.
- `BUY_INPUTS` has a hard sell barrier: bank/inventory/live-offer plank stock always routes back to `SELL_OUTPUTS`.
- Exact bank-ID counting for logs, planks, coins, tablets, stamina doses and Rings of wealth.
- Banking verifies bank widgets, withdraw mode and inventory changes after deposit/withdraw actions.
- `withdrawOne()` for Stamina potions, Rings of wealth and Civitas tablets.
- Ring of wealth remains mandatory for GE return when expected, preventing the generic walker from preferring a Combat bracelet transport.
- Optional `[FastPlanker DEBUG]` heartbeat/action logging.

## Existing production/travel behavior

- Regular/Oak/Teak/Mahogany/Camphor/Ironwood/Rosewood recipes.
- Existing configured logs are consumed before buying more.
- Lower-dose stamina first and lower-charge Ring of wealth first.
- Civitas tablet -> Renu/Quetzal -> Auburnvale safe route.
- Conditional production `All` selection.
- Uncharged Ring of wealth sell threshold.
- Parallel configured-plank + uncharged-ring sell offers when both are eligible.

## Break Handler implementation

Fast Planker is now self-contained. It does **not** require the optional `kspsupport` source package. The local `KspAuburnvaleBreakService` mirrors the KSP break ownership model: it randomizes runtime and break duration, waits for a safe logout, prevents AutoLogin from shortening the break, returns to the previous world, and only releases a global pause it acquired itself.

## Grand Exchange implementation

Initial offers continue to use Microbot's normal Grand Exchange helpers. Repricing an already-live sell offer now opens that exact occupied slot through **Modify offer**, edits only its price, confirms it, and verifies that the same live slot reports the new price. The repricing path contains no `abortOffer()` call.
