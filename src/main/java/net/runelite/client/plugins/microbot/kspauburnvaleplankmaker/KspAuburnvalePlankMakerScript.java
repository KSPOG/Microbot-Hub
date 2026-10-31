package net.runelite.client.plugins.microbot.kspauburnvaleplankmaker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspAuburnvalePlankMakerScript extends Script {
    private static final WorldPoint GE_POINT = BankLocation.GRAND_EXCHANGE.getWorldPoint();
    private static final WorldPoint AUBURNVALE_BANK = BankLocation.AUBURNVALE.getWorldPoint();
    private static final WorldPoint CIVITAS_QUETZAL = new WorldPoint(1697, 3140, 0);
    private static final WorldPoint AUBURNVALE_QUETZAL_LANDING = new WorldPoint(1411, 3361, 0);

    // Only an approach point is needed. The actual operator is resolved dynamically from the NPC cache.
    private static final WorldPoint AUBURNVALE_SAWMILL_APPROACH = new WorldPoint(1395, 3366, 0);
    private static final WorldPoint CIVITAS_TELEPORT_AREA = new WorldPoint(1681, 3133, 0);

    private static final int CIVITAS_TABLET = ItemID.POH_TABLET_FORTISTELEPORT;

    // Consumption order is deliberate: finish partial potions before touching a fresh 4-dose potion.
    private static final int[] STAMINA_IDS = {
            ItemID._1DOSESTAMINA,
            ItemID._2DOSESTAMINA,
            ItemID._3DOSESTAMINA,
            ItemID._4DOSESTAMINA
    };

    // Same idea for Ring of wealth charges: consume partial rings before a fresh five-charge ring.
    private static final int[] CHARGED_ROW_IDS = {
            ItemID.RING_OF_WEALTH_1,
            ItemID.RING_OF_WEALTH_2,
            ItemID.RING_OF_WEALTH_3,
            ItemID.RING_OF_WEALTH_4,
            ItemID.RING_OF_WEALTH_5
    };

    private final KspAuburnvalePlankMakerConfig config;
    private final Map<Integer, PriceSnapshot> priceCache = new HashMap<>();
    private final Map<Integer, SellOfferWaitState> sellOfferWaitStates = new HashMap<>();
    private final KspAuburnvaleWorldHopper worldHopper;

    private volatile Phase phase = Phase.TO_GE;
    private volatile long startedAt;
    private volatile long planksMade;
    private volatile long sawmillTrips;
    private volatile long geCycles;
    private volatile long lastSawmillNpcClickAt;
    private volatile boolean sellPlanksRequested;
    private volatile long lastDebugSnapshotAt;
    private volatile String lastDebugAction = "startup";
    private volatile long geModifyProtectionUntil;

    public KspAuburnvalePlankMakerScript(KspAuburnvalePlankMakerConfig config) {
        this.config = config;
        this.worldHopper = new KspAuburnvaleWorldHopper(config);
    }

    public boolean run() {
        startedAt = System.currentTimeMillis();
        worldHopper.start();

        WorldPoint start = Rs2Player.getWorldLocation();
        if (start != null && start.getPlane() == AUBURNVALE_BANK.getPlane()
                && start.distanceTo2D(AUBURNVALE_BANK) <= 35) {
            // If started in Auburnvale, inspect/process already-owned logs immediately
            // rather than pointlessly teleporting to the GE first.
            phase = Phase.BANK_LOAD;
        } else {
            phase = isAtGrandExchange() ? Phase.SELL_OUTPUTS : Phase.TO_GE;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || KspAuburnvaleBreakService.shouldPause() || !super.run()) {
                    return;
                }

                publishDebugSnapshot(false);

                if (worldHopper.tick()) {
                    return;
                }

                switch (phase) {
                    case TO_GE:
                        if (travelToGrandExchange()) {
                            phase = Phase.SELL_OUTPUTS;
                        }
                        break;

                    case SELL_OUTPUTS:
                        if (sellOutputsBeforeInputs()) {
                            phase = Phase.BUY_INPUTS;
                        }
                        break;

                    case BUY_INPUTS:
                        if (restockInputs()) {
                            geCycles++;
                            phase = Phase.PREPARE_AUBURNVALE;
                        }
                        break;

                    case PREPARE_AUBURNVALE:
                        if (prepareAuburnvaleTrip()) {
                            sellPlanksRequested = false;
                            phase = Phase.TELEPORT_CIVITAS;
                        }
                        break;

                    case TELEPORT_CIVITAS:
                        if (teleportToCivitas()) {
                            phase = Phase.WALK_TO_AUBURNVALE;
                        }
                        break;

                    case WALK_TO_AUBURNVALE:
                        if (isNearAuburnvaleBank() || isNearAuburnvaleQuetzalLanding()) {
                            Microbot.status = "Auburnvale reached - preparing bank";
                            phase = Phase.BANK_LOAD;
                            break;
                        }

                        if (travelToAuburnvaleViaQuetzal()) {
                            Microbot.status = "Quetzal arrived in Auburnvale";
                            phase = Phase.BANK_LOAD;
                        }
                        break;

                    case BANK_LOAD:
                        if (canProcessInventoryLogs()) {
                            if (Rs2Bank.isOpen() && !closeBankAndWait()) {
                                break;
                            }

                            phase = Phase.WALK_TO_SAWMILL;

                            // Start the bank -> sawmill leg immediately instead of
                            // waiting for the next 600 ms scheduler pass.
                            if (walkTowardSawmill()) {
                                phase = Phase.MAKE_PLANKS;
                            }
                        } else if (shouldSellPlanks()) {
                            prepareReturnToGeFromAuburnvale(true);
                        } else if (shouldSellUnchargedRings()) {
                            prepareReturnToGeFromAuburnvale(false);
                        } else {
                            prepareSawmillLoad();
                        }
                        break;

                    case WALK_TO_SAWMILL:
                        if (walkTowardSawmill()) {
                            phase = Phase.MAKE_PLANKS;
                        }
                        break;

                    case MAKE_PLANKS:
                        makePlanks();
                        break;

                    case RETURN_TO_BANK:
                        if (walkTowardAuburnvaleBank()) {
                            phase = Phase.BANK_LOAD;
                        }
                        break;

                    case STOPPED:
                        break;
                }
            } catch (Exception ex) {
                debug("EXCEPTION phase=%s action=%s type=%s message=%s",
                        phase, lastDebugAction, ex.getClass().getSimpleName(), String.valueOf(ex.getMessage()));
                publishDebugSnapshot(true);
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                Microbot.status = "Recovered from " + phase + " error";
                sleep(1_500);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean travelToGrandExchange() {
        if (isAtGrandExchange()) {
            return true;
        }

        Microbot.status = "Preparing Ring of wealth";
        int inventoryRing = firstInventoryItem(CHARGED_ROW_IDS);

        if (inventoryRing == -1) {
            if (!Rs2Bank.isOpen()) {
                traceAction("row:open-bank");
                if (!Rs2Bank.walkToBankAndUseBank()) {
                    Microbot.status = "Walking to bank for Ring of wealth";
                    return false;
                }
                if (!sleepUntil(Rs2Bank::isOpen, 5_000)) {
                    Microbot.status = "Waiting for bank widget";
                    return false;
                }
            }

            if (!ensureBankWithdrawAsItem("ROW")) {
                return false;
            }

            inventoryRing = withdrawLowestChargedRing();
            if (inventoryRing == -1) {
                // Do not hand this trip to the generic walker: it may choose a
                // Combat bracelet or another transport even when the plugin is
                // designed around Ring of wealth. Stay at the bank and report it.
                Microbot.status = "No charged Ring of wealth available";
                debug("ROW missing: refusing generic GE transport fallback");
                return false;
            }

            if (!closeBankAndWait()) {
                return false;
            }
        }

        // Re-read inventory after bank close; never trust a stale local ID.
        inventoryRing = firstInventoryItem(CHARGED_ROW_IDS);
        if (inventoryRing == -1) {
            Microbot.status = "Waiting for charged Ring of wealth in inventory";
            return false;
        }

        Microbot.status = "Ring of wealth -> Grand Exchange";
        traceAction("row:rub id=" + inventoryRing);
        if (!Rs2Inventory.interact(inventoryRing, "Rub")) {
            return false;
        }

        if (!Rs2Dialogue.sleepUntilSelectAnOption()) {
            Microbot.status = "Waiting for Ring of wealth dialogue";
            return false;
        }
        if (!Rs2Dialogue.sleepUntilHasDialogueOption("Grand Exchange")) {
            Microbot.status = "Waiting for Grand Exchange option";
            return false;
        }
        if (!Rs2Dialogue.clickOption("Grand Exchange", true)) {
            return false;
        }
        return sleepUntil(this::isAtGrandExchange, 8_000);
    }

    /**
     * Hard ordering guarantee:
     * 1. configured plank output
     * 2. uncharged Rings of wealth
     * 3. only after both are gone may BUY_INPUTS execute
     */
    private boolean sellOutputsBeforeInputs() {
        Microbot.status = "GE: checking sellable outputs";
        PlankType type = config.plankType();

        // Existing offers always have priority. Completed offers are collected,
        // slow active offers are repriced, and BUY_INPUTS cannot run while a
        // liquidation offer is still alive.
        if (handleExistingSellOffers(type)) {
            return false;
        }

        if (!prepareGeBank()) {
            return false;
        }

        int bankedOutputPlanks = bankOutputPlanks(type);
        int emptyRingCount = exactBankCount(ItemID.RING_OF_WEALTH);

        // Reaching the threshold starts a liquidation cycle. Once started, sell
        // every remaining configured plank even when the remainder falls below
        // the configured threshold.
        if (!sellPlanksRequested && bankedOutputPlanks >= config.sellPlanksAt()) {
            sellPlanksRequested = true;
        }
        if (sellPlanksRequested && bankedOutputPlanks <= 0) {
            sellPlanksRequested = false;
        }

        boolean sellPlanks = sellPlanksRequested && bankedOutputPlanks > 0;
        boolean sellUnchargedRings = emptyRingCount >= config.sellUnchargedRingsAt();

        if (!sellPlanks && !sellUnchargedRings) {
            Microbot.status = "GE: keeping " + bankedOutputPlanks + "/"
                    + config.sellPlanksAt() + " planks, "
                    + emptyRingCount + "/" + config.sellUnchargedRingsAt()
                    + " uncharged rings";
            return true;
        }

        if (!Rs2Bank.hasWithdrawAsNote()) {
            traceAction("ge-sell:set-withdraw-notes");
            if (!Rs2Bank.setWithdrawAsNote()) {
                return false;
            }
            if (!sleepUntil(Rs2Bank::hasWithdrawAsNote, 2_000)) {
                Microbot.status = "Waiting for bank note mode";
                return false;
            }
        }

        if (sellPlanks) {
            String plankName = itemName(type.getOutputPlankId());
            int before = Rs2Inventory.itemQuantity(plankName, true);
            traceAction("ge-sell:withdraw-planks qty=" + bankedOutputPlanks);
            if (!Rs2Bank.withdrawAll(type.getOutputPlankId())) {
                return false;
            }
            if (!sleepUntil(() -> Rs2Inventory.itemQuantity(plankName, true) > before, 3_000)) {
                Microbot.status = "Waiting for plank sell stack";
                return false;
            }
        }

        if (sellUnchargedRings) {
            String ringName = itemName(ItemID.RING_OF_WEALTH);
            int before = Rs2Inventory.itemQuantity(ringName, true);
            traceAction("ge-sell:withdraw-rings qty=" + emptyRingCount);
            if (!Rs2Bank.withdrawAll(ItemID.RING_OF_WEALTH)) {
                return false;
            }
            if (!sleepUntil(() -> Rs2Inventory.itemQuantity(ringName, true) > before, 3_000)) {
                Microbot.status = "Waiting for uncharged ring sell stack";
                return false;
            }
        }

        if (!closeBankAndWait() || !openGe()) {
            return false;
        }

        GrandExchangeSlots plankSlot = null;
        GrandExchangeSlots ringSlot = null;

        if (sellPlanks && Rs2GrandExchange.findSlotForItem(type.getOutputPlankId(), true) == null) {
            plankSlot = placeSellOfferAtGe(type.getOutputPlankId());
            if (plankSlot == null) {
                Microbot.status = "Waiting to place plank sell offer";
                return false;
            }
        }

        if (sellUnchargedRings && Rs2GrandExchange.findSlotForItem(ItemID.RING_OF_WEALTH, true) == null) {
            ringSlot = placeSellOfferAtGe(ItemID.RING_OF_WEALTH);
            if (ringSlot == null) {
                // Keep a valid plank offer alive; never cancel it just because a
                // second slot failed to open this tick.
                Microbot.status = "Waiting to place uncharged ring sell offer";
                return false;
            }
        }

        Microbot.status = "GE: sell offers active";
        debug("SELL placed plankSlot=%s ringSlot=%s", String.valueOf(plankSlot), String.valueOf(ringSlot));
        return false;
    }

    private GrandExchangeSlots placeSellOfferAtGe(int itemId) {
        if (!Rs2GrandExchange.isOpen() && !openGe()) {
            return null;
        }

        String name = itemName(itemId);
        int quantity = Rs2Inventory.itemQuantity(name, true);
        if (quantity <= 0) {
            Microbot.status = "Sell stack missing from inventory: " + name;
            return null;
        }

        // Start at the reference price. The configured sell threshold is the
        // floor, not the initial offer. Slow offers step toward that floor.
        int market = marketPrice(itemId);
        int floor = thresholdPrice(itemId, false);
        if (market <= 0 || floor <= 0) {
            Microbot.status = "No verified GE price for " + name;
            return null;
        }
        int price = Math.max(floor, market);

        traceAction("ge-sell:place " + quantity + "x " + name + " @" + price);
        Microbot.status = "Selling " + quantity + " x " + name + " @ " + price;
        if (!Rs2GrandExchange.sellItem(name, quantity, price)) {
            return null;
        }

        GrandExchangeSlots slot = waitForOfferSlot(itemId, true);
        if (slot == null) {
            Microbot.status = "Waiting for sell slot: " + name;
            return null;
        }

        GeOfferSnapshot snapshot = geOfferSnapshot(slot);
        if (snapshot == null || snapshot.itemId != itemId || !snapshot.selling) {
            Microbot.status = "Waiting for sell offer confirmation: " + name;
            return null;
        }

        GeOfferSnapshot liveOffer = geOfferSnapshot(slot);
        if (liveOffer != null) {
            resetSellOfferWait(itemId, slot, liveOffer, System.currentTimeMillis());
        }
        return slot;
    }

    private boolean handleExistingSellOffers(PlankType type) {
        GrandExchangeSlots plankSlot = Rs2GrandExchange.findSlotForItem(type.getOutputPlankId(), true);
        GrandExchangeSlots ringSlot = Rs2GrandExchange.findSlotForItem(ItemID.RING_OF_WEALTH, true);

        if (plankSlot == null && ringSlot == null) {
            if (System.currentTimeMillis() < geModifyProtectionUntil) {
                Microbot.status = "GE: waiting for modified offer state";
                return true;
            }
            return false;
        }
        if (!Rs2GrandExchange.isOpen() && !openGe()) {
            return true;
        }

        boolean acted = false;
        if (plankSlot != null) {
            acted |= manageExistingSellOffer(type.getOutputPlankId(), plankSlot);
        }
        if (ringSlot != null) {
            acted |= manageExistingSellOffer(ItemID.RING_OF_WEALTH, ringSlot);
        }
        if (!acted) {
            Microbot.status = "GE: waiting for active sell offers";
        }
        return true;
    }

    private boolean manageExistingSellOffer(int itemId, GrandExchangeSlots slot) {
        String name = itemName(itemId);
        GeOfferSnapshot offer = geOfferSnapshot(slot);
        if (offer == null) {
            Microbot.status = "GE: waiting for offer state: " + name;
            return false;
        }

        if (offer.state == GrandExchangeOfferState.SOLD || offer.quantitySold >= offer.totalQuantity) {
            Microbot.status = "GE: collecting sold " + name;
            traceAction("ge-sell:collect-complete " + name);
            if (!Rs2GrandExchange.collectAllToBank()) {
                return false;
            }
            boolean cleared = sleepUntil(() -> Rs2GrandExchange.findSlotForItem(itemId, true) == null, 4_000);
            if (cleared) {
                sellOfferWaitStates.remove(itemId);
                if (itemId == config.plankType().getOutputPlankId()) {
                    // Keep liquidation active until the bank is checked and no
                    // configured planks remain.
                    sellPlanksRequested = true;
                }
            }
            return cleared;
        }

        int remaining = Math.max(0, offer.totalQuantity - offer.quantitySold);
        if (remaining <= 0) {
            Microbot.status = "GE: waiting to collect " + name;
            return false;
        }

        long now = System.currentTimeMillis();
        long waitRemaining = sellOfferWaitRemainingMillis(itemId, slot, offer, now);
        if (waitRemaining > 0L) {
            long seconds = Math.max(1L, (waitRemaining + 999L) / 1_000L);
            Microbot.status = "GE: " + remaining + " " + name
                    + " remaining; sell wait " + seconds + "s";
            return false;
        }

        priceCache.remove(itemId);
        int floor = thresholdPrice(itemId, false);
        if (floor <= 0) {
            Microbot.status = "GE: waiting for refreshed price: " + name;
            resetSellOfferWait(itemId, slot, offer, now);
            return false;
        }

        int step = Math.max(1, offer.price / 100);
        int nextPrice = Math.max(floor, offer.price - step);
        if (nextPrice >= offer.price) {
            // Already at the configured floor. Keep the offer live and block
            // input buying until it really finishes.
            Microbot.status = "GE: " + name + " at sell floor; waiting for fill";
            return false;
        }

        return modifySellOffer(itemId, slot, remaining, nextPrice);
    }

    private boolean modifySellOffer(int itemId, GrandExchangeSlots slot, int remainingQuantity, int newPrice) {
        String name = itemName(itemId);
        Microbot.status = "GE: modifying " + name + " -> " + newPrice;
        traceAction("ge-sell:modify " + name + " remaining=" + remainingQuantity + " @" + newPrice);

        GeOfferSnapshot before = geOfferSnapshot(slot);
        if (before == null || before.itemId != itemId || !before.selling) {
            Microbot.status = "GE: waiting for live sell offer before modify";
            return false;
        }
        if (before.price == newPrice) {
            resetSellOfferWait(itemId, slot, before, System.currentTimeMillis());
            return true;
        }
        if (!ensureGeOverviewForModify()) {
            return false;
        }

        Widget slotWidget = Rs2Widget.getWidget(InterfaceID.GeOffers.INDEX_0 + slot.ordinal());
        if (slotWidget == null) {
            Microbot.status = "GE: waiting for offer slot widget";
            return false;
        }

        // Protect the slot before opening the editor. RuneLite's live GE offer array
        // can briefly transition while Jagex applies Modify offer; that transient
        // must never be treated as permission to place a second sell offer.
        geModifyProtectionUntil = System.currentTimeMillis() + 5_000L;

        // Poll 84 added a real Modify offer action. Identifier 3 on an occupied
        // GE slot is the same proven path used by KSP Jewelry Crafter/Smart Superheat.
        Rs2Widget.clickWidgetFast(slotWidget, 2, 3);
        if (!sleepUntil(() -> geSetupOpen() || Rs2GrandExchange.isOfferScreenOpen()
                || !Rs2GrandExchange.isOpen(), 3_500)) {
            Microbot.status = "GE: waiting for Modify offer";
            return false;
        }
        if (!Rs2GrandExchange.isOpen()) {
            Microbot.status = "GE closed while opening Modify offer";
            return false;
        }

        // Some client builds open the details page first. Use its Modify button
        // rather than aborting the offer.
        if (!geSetupOpen()) {
            Widget modify = Rs2Widget.getWidget(InterfaceID.GeOffers.DETAILS_MODIFY);
            if (modify == null || !Rs2Widget.clickWidget(modify)) {
                Microbot.status = "GE: waiting for Modify offer button";
                return false;
            }
            if (!sleepUntil(this::geSetupOpen, 3_000)) {
                Microbot.status = "GE: waiting for offer editor";
                return false;
            }
        }

        Widget setup = Rs2Widget.getWidget(InterfaceID.GeOffers.SETUP);
        Widget priceX = setup == null ? null : setup.getChild(12);
        if (priceX == null || !Rs2Widget.clickWidget(priceX)) {
            Microbot.status = "GE: unable to open modify price input";
            return false;
        }
        if (!sleepUntil(this::gePriceInputOpen, 2_500)) {
            Microbot.status = "GE: waiting for modify price input";
            return false;
        }

        // Match the existing KSP GE flows: set the chatbox value, submit it,
        // then confirm the modified offer and verify the live slot price.
        sleep(250, 450);
        Rs2GrandExchange.setChatboxValue(newPrice);
        sleep(200, 350);
        Rs2Keyboard.enter();
        if (!sleepUntil(() -> !gePriceInputOpen(), 2_500)) {
            Microbot.status = "GE: waiting for modified price entry";
            return false;
        }

        if (!Rs2Widget.clickWidget(InterfaceID.GeOffers.SETUP_CONFIRM)) {
            Microbot.status = "GE: unable to confirm modified offer";
            return false;
        }
        sleepUntil(() -> !geSetupOpen() || Rs2Widget.hasWidget("Your offer is much"), 3_000);
        if (Rs2Widget.hasWidget("Your offer is much")) {
            if (!Rs2Widget.clickWidget("Yes")) {
                Microbot.status = "GE: waiting for price warning confirmation";
                return false;
            }
            sleepUntil(() -> !geSetupOpen(), 3_000);
        }

        boolean changed = sleepUntil(() -> {
            GeOfferSnapshot snapshot = geOfferSnapshot(slot);
            return snapshot != null && snapshot.itemId == itemId
                    && snapshot.selling && snapshot.price == newPrice;
        }, 4_000);
        if (!changed) {
            Microbot.status = "GE: waiting for modified " + name + " offer";
            return false;
        }

        long now = System.currentTimeMillis();
        geModifyProtectionUntil = now + 5_000L;
        GeOfferSnapshot modified = geOfferSnapshot(slot);
        if (modified != null) {
            resetSellOfferWait(itemId, slot, modified, now);
        } else {
            sellOfferWaitStates.remove(itemId);
        }
        return true;
    }

    /**
     * Enforces the configured sell wait against the live GE offer, not merely the
     * item id. Any real offer activity (new slot, price change, quantity change,
     * or partial fill) starts a fresh full wait interval before Modify is allowed.
     */
    private long sellOfferWaitRemainingMillis(int itemId, GrandExchangeSlots slot,
                                              GeOfferSnapshot offer, long now) {
        SellOfferWaitState state = sellOfferWaitStates.get(itemId);
        boolean differentOffer = state == null
                || state.slotOrdinal != slot.ordinal()
                || state.price != offer.price
                || state.totalQuantity != offer.totalQuantity;

        if (differentOffer) {
            state = new SellOfferWaitState(slot.ordinal(), offer.price, offer.totalQuantity,
                    offer.quantitySold, now);
            sellOfferWaitStates.put(itemId, state);
            debug("GE sell wait started item=%s slot=%s price=%d sold=%d/%d wait=%ds",
                    itemName(itemId), slot, offer.price, offer.quantitySold, offer.totalQuantity,
                    Math.max(5, config.offerWaitSeconds()));
        } else if (offer.quantitySold != state.quantitySold) {
            // The offer is still making progress. Give it the user's complete
            // wait interval again instead of repricing immediately after a fill.
            state.quantitySold = offer.quantitySold;
            state.lastActivityAt = now;
            debug("GE sell wait reset after fill item=%s sold=%d/%d wait=%ds",
                    itemName(itemId), offer.quantitySold, offer.totalQuantity,
                    Math.max(5, config.offerWaitSeconds()));
        }

        long elapsed = Math.max(0L, now - state.lastActivityAt);
        return Math.max(0L, (long) offerWaitMillis() - elapsed);
    }

    private void resetSellOfferWait(int itemId, GrandExchangeSlots slot,
                                    GeOfferSnapshot offer, long now) {
        sellOfferWaitStates.put(itemId, new SellOfferWaitState(slot.ordinal(), offer.price,
                offer.totalQuantity, offer.quantitySold, now));
    }

    private boolean ensureGeOverviewForModify() {
        if (!Rs2GrandExchange.isOpen() && !openGe()) return false;
        if (!Rs2GrandExchange.isOfferScreenOpen() && !geSetupOpen()) return true;
        Rs2GrandExchange.backToOverview();
        return sleepUntil(() -> Rs2GrandExchange.isOpen()
                && !Rs2GrandExchange.isOfferScreenOpen() && !geSetupOpen(), 4_000);
    }

    private boolean geSetupOpen() {
        return Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP);
    }

    private boolean gePriceInputOpen() {
        Widget input = Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        return input != null && !input.isHidden();
    }

    private boolean restockInputs() {
        Microbot.status = "GE: calculating restock batch";

        if (!prepareGeBank()) {
            return false;
        }

        PlankType type = config.plankType();

        // Hard liquidation barrier: after the sell threshold is crossed, logs
        // are never bought until every configured plank has left bank/inventory
        // and no live plank sell offer remains.
        if (sellPlanksRequested) {
            int banked = bankOutputPlanks(type);
            int carried = inventoryOutputPlanks(type);
            GrandExchangeSlots live = Rs2GrandExchange.findSlotForItem(type.getOutputPlankId(), true);
            if (banked > 0 || carried > 0 || live != null) {
                Microbot.status = "GE: finish selling all planks before buying logs";
                phase = Phase.SELL_OUTPUTS;
                return false;
            }
            sellPlanksRequested = false;
        }

        int currentRings = countBank(CHARGED_ROW_IDS);
        int currentTablets = exactBankCount(CIVITAS_TABLET);
        int currentStamina = config.useStaminaPotions() ? countBank(STAMINA_IDS) : 0;
        int existingInputLogs = bankInputLogs(type);
        int coins = exactBankCount(ItemID.COINS);

        int ringDeficit = Math.max(0, config.ringRestockAmount() - currentRings);
        int tabletDeficit = Math.max(0, config.civitasTabletRestockAmount() - currentTablets);
        int staminaDeficit = config.useStaminaPotions()
                ? Math.max(0, config.staminaRestockAmount() - currentStamina)
                : 0;

        int ringPrice = ringDeficit > 0 ? thresholdPrice(ItemID.RING_OF_WEALTH_5, true) : 0;
        int tabletPrice = tabletDeficit > 0 ? thresholdPrice(CIVITAS_TABLET, true) : 0;
        int staminaPrice = staminaDeficit > 0 ? thresholdPrice(ItemID._4DOSESTAMINA, true) : 0;
        int logPrice = existingInputLogs <= 0 ? thresholdPrice(type.getInputLogId(), true) : 0;

        if ((ringDeficit > 0 && ringPrice <= 0)
                || (tabletDeficit > 0 && tabletPrice <= 0)
                || (staminaDeficit > 0 && staminaPrice <= 0)
                || (existingInputLogs <= 0 && logPrice <= 0)) {
            Microbot.status = "Waiting for GE prices";
            return false;
        }

        long nonLogCost = (long) ringDeficit * ringPrice
                + (long) tabletDeficit * tabletPrice
                + (long) staminaDeficit * staminaPrice;

        int logsToBuy = 0;
        if (existingInputLogs <= 0) {
            long remaining = Math.max(0L, (long) coins - nonLogCost);
            long perLogWithFee = (long) logPrice + type.getSawmillFee();
            int affordable = perLogWithFee <= 0 ? 0
                    : (int) Math.min(Integer.MAX_VALUE, remaining / perLogWithFee);
            logsToBuy = Math.min(config.logBuyAmount(), affordable);
            if (logsToBuy <= 0) {
                stop("Not enough coins to buy logs and reserve sawmill fees");
                return false;
            }
        } else {
            Microbot.status = "Using " + existingInputLogs + " existing " + type.getInputLogName();
        }

        boolean needsGe = ringDeficit > 0 || tabletDeficit > 0 || staminaDeficit > 0 || logsToBuy > 0;
        if (!needsGe) {
            return true;
        }

        if (!closeBankAndWait() || !openGe()) {
            return false;
        }

        // One verified offer at a time. Each helper waits for the offer widget,
        // completion, collection, and slot reset before the next offer begins.
        if (ringDeficit > 0 && !buyItemAtGe(ItemID.RING_OF_WEALTH_5, ringDeficit, ringPrice)) return false;
        if (tabletDeficit > 0 && !buyItemAtGe(CIVITAS_TABLET, tabletDeficit, tabletPrice)) return false;
        if (staminaDeficit > 0 && !buyItemAtGe(ItemID._4DOSESTAMINA, staminaDeficit, staminaPrice)) return false;
        if (logsToBuy > 0 && !buyItemAtGe(type.getInputLogId(), logsToBuy, logPrice)) return false;

        // Do not assume collection succeeded. Re-open the bank and verify the
        // exact configured input ID before PREPARE_AUBURNVALE can run.
        if (!prepareGeBank()) {
            return false;
        }
        int verifiedLogs = bankInputLogs(type);
        if (verifiedLogs <= 0) {
            Microbot.status = "GE: configured " + type.getInputLogName() + " not yet in bank";
            return false;
        }
        Microbot.status = "GE: verified " + verifiedLogs + " " + type.getInputLogName();
        return true;
    }

    private boolean buyItemAtGe(int itemId, int quantity, int maxPrice) {
        if (quantity <= 0) return true;
        if (!Rs2GrandExchange.isOpen() && !openGe()) return false;

        String name = itemName(itemId);
        GrandExchangeSlots existing = Rs2GrandExchange.findSlotForItem(itemId, false);
        if (existing != null) {
            GeOfferSnapshot offer = geOfferSnapshot(existing);
            if (offer != null && (offer.state == GrandExchangeOfferState.BOUGHT
                    || offer.quantitySold >= offer.totalQuantity)) {
                Microbot.status = "GE: collecting bought " + name;
                traceAction("ge-buy:collect " + name);
                if (!Rs2GrandExchange.collectAllToBank()) return false;
                return sleepUntil(() -> Rs2GrandExchange.findSlotForItem(itemId, false) == null, 4_000);
            }
            Microbot.status = "GE: waiting for " + name + " buy offer";
            return false;
        }

        // Buy at the configured ceiling so we never cross the user's maximum.
        // A timed-out ceiling offer remains live instead of being cancelled and
        // recreated every cycle.
        traceAction("ge-buy:place " + quantity + "x " + name + " @" + maxPrice);
        Microbot.status = "Buying " + quantity + " x " + name + " @ " + maxPrice;
        if (!Rs2GrandExchange.buyItem(name, maxPrice, quantity)) {
            return false;
        }

        GrandExchangeSlots slot = waitForOfferSlot(itemId, false);
        if (slot == null) {
            Microbot.status = "Waiting for buy slot: " + name;
            return false;
        }

        boolean completed = sleepUntil(() -> {
            GeOfferSnapshot offer = geOfferSnapshot(slot);
            return offer != null && (offer.state == GrandExchangeOfferState.BOUGHT
                    || offer.quantitySold >= offer.totalQuantity);
        }, offerWaitMillis());

        if (!completed) {
            GeOfferSnapshot offer = geOfferSnapshot(slot);
            int remaining = offer == null ? quantity : Math.max(0, offer.totalQuantity - offer.quantitySold);
            Microbot.status = "GE: " + remaining + " " + name + " remaining at buy ceiling";
            // Keep it active. The next BUY_INPUTS tick observes the same slot and
            // waits rather than cancelling/recreating the offer too quickly.
            return false;
        }

        traceAction("ge-buy:collect-complete " + name);
        if (!Rs2GrandExchange.collectAllToBank()) {
            Microbot.status = "Collecting bought " + name;
            return false;
        }
        return sleepUntil(() -> Rs2GrandExchange.findSlotForItem(itemId, false) == null, 4_000);
    }



    private boolean prepareAuburnvaleTrip() {
        if (!prepareGeBankPreservingInventory()) {
            return false;
        }
        if (!ensureBankWithdrawAsItem("PREP_TRIP")) {
            return false;
        }

        // Keep only valid travel supplies. Tablets are stackable, so any positive
        // quantity is valid and must not be repeatedly deposited/withdrawn.
        if (!travelInventoryOnly()) {
            traceAction("prep:deposit-unwanted");
            Rs2Bank.depositAllExcept(
                    CIVITAS_TABLET,
                    ItemID._1DOSESTAMINA,
                    ItemID._2DOSESTAMINA,
                    ItemID._3DOSESTAMINA,
                    ItemID._4DOSESTAMINA
            );
            if (!sleepUntil(this::travelInventoryOnly, 3_000)) {
                Microbot.status = "Waiting for travel inventory cleanup";
                return false;
            }
        }

        if (!Rs2Inventory.hasItem(CIVITAS_TABLET)) {
            if (exactBankCount(CIVITAS_TABLET) <= 0) {
                Microbot.status = "No Civitas teleport tablets in bank";
                phase = Phase.BUY_INPUTS;
                return false;
            }
            traceAction("prep:withdraw-tablet-one");
            if (!Rs2Bank.withdrawOne(CIVITAS_TABLET)) return false;
            if (!sleepUntil(() -> Rs2Inventory.hasItem(CIVITAS_TABLET), 3_000)) {
                Microbot.status = "Waiting for Civitas teleport tablet";
                return false;
            }
        }

        if (config.useStaminaPotions() && firstInventoryItem(STAMINA_IDS) == -1) {
            if (withdrawPreferredStamina() == -1) {
                Microbot.status = "No Stamina potion available";
                return false;
            }
        }

        if (!Rs2Inventory.hasItem(CIVITAS_TABLET)) {
            return false;
        }
        if (!closeBankAndWait()) return false;
        return Rs2Inventory.hasItem(CIVITAS_TABLET);
    }

    private boolean travelInventoryOnly() {
        List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> items = Rs2Inventory.all();
        if (items == null) return false;
        for (var item : items) {
            if (item == null || item.getQuantity() <= 0) continue;
            int id = item.getId();
            if (id == CIVITAS_TABLET) continue;
            boolean stamina = false;
            for (int sid : STAMINA_IDS) if (sid == id) { stamina = true; break; }
            if (config.useStaminaPotions() && stamina) continue;
            return false;
        }
        return true;
    }

    private boolean teleportToCivitas() {
        if (!Rs2Inventory.hasItem(CIVITAS_TABLET)) {
            phase = Phase.PREPARE_AUBURNVALE;
            return false;
        }

        Microbot.status = "Civitas illa Fortis teleport";
        if (!Rs2Inventory.interact(CIVITAS_TABLET, "Break")) {
            return false;
        }

        return sleepUntil(() -> {
            WorldPoint here = Rs2Player.getWorldLocation();
            return here != null
                    && here.getPlane() == CIVITAS_TELEPORT_AREA.getPlane()
                    && here.distanceTo2D(CIVITAS_TELEPORT_AREA) <= 20;
        }, 8_000);
    }

    private int inventoryInputLogs(PlankType type) {
        return Rs2Inventory.itemQuantity(type.getInputLogId());
    }

    private int bankInputLogs(PlankType type) {
        return exactBankCount(type.getInputLogId());
    }

    private int inventoryOutputPlanks(PlankType type) {
        return Rs2Inventory.itemQuantity(type.getOutputPlankId());
    }

    private int bankOutputPlanks(PlankType type) {
        return exactBankCount(type.getOutputPlankId());
    }

    private boolean canProcessInventoryLogs() {
        PlankType type = config.plankType();
        int logs = inventoryInputLogs(type);

        if (logs <= 0) {
            return false;
        }

        long requiredCoins = (long) logs * type.getSawmillFee();
        return Rs2Inventory.itemQuantity(ItemID.COINS) >= requiredCoins;
    }

    private void prepareSawmillLoad() {
        Microbot.status = "Auburnvale: preparing load";

        if (!Rs2Bank.isOpen()) {
            traceAction("bankload:open-auburnvale-bank");
            if (!Rs2Bank.walkToBankAndUseBank(BankLocation.AUBURNVALE)) return;
            if (!sleepUntil(Rs2Bank::isOpen, 5_000)) {
                Microbot.status = "Waiting for Auburnvale bank widget";
                return;
            }
        }
        if (!ensureBankWithdrawAsItem("BANK_LOAD")) return;

        PlankType type = config.plankType();

        if (!sawmillInventoryOnly(type)) {
            traceAction("bankload:deposit-unwanted");
            Rs2Bank.depositAllExcept(
                    ItemID.COINS,
                    type.getInputLogId(),
                    ItemID._1DOSESTAMINA,
                    ItemID._2DOSESTAMINA,
                    ItemID._3DOSESTAMINA,
                    ItemID._4DOSESTAMINA
            );
            if (!sleepUntil(() -> sawmillInventoryOnly(type), 3_000)) {
                Microbot.status = "Waiting for bank deposits";
                return;
            }
        }

        int inventoryLogs = inventoryInputLogs(type);
        int bankLogs = bankInputLogs(type);
        int bankPlanks = bankOutputPlanks(type);
        Microbot.status = "Bank: " + bankLogs + " " + type.getInputLogName()
                + " | " + bankPlanks + " " + type.getOutputPlankName();

        if (inventoryLogs <= 0 && bankLogs <= 0) {
            prepareReturnToGeFromAuburnvale(false);
            return;
        }

        if (config.useStaminaPotions() && firstInventoryItem(STAMINA_IDS) == -1) {
            withdrawPreferredStamina();
            return;
        }

        int inventoryCoins = Rs2Inventory.itemQuantity(ItemID.COINS);
        int bankCoins = exactBankCount(ItemID.COINS);
        long totalCoins = (long) inventoryCoins + bankCoins;
        if (totalCoins < type.getSawmillFee()) {
            prepareReturnToGeFromAuburnvale(false);
            return;
        }

        if (bankCoins > 0) {
            int before = inventoryCoins;
            traceAction("bankload:withdraw-all-coins");
            if (!Rs2Bank.withdrawAll(ItemID.COINS)) return;
            if (!sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS) > before, 3_000)) {
                Microbot.status = "Waiting for all coins";
                return;
            }
            return;
        }

        inventoryCoins = Rs2Inventory.itemQuantity(ItemID.COINS);
        inventoryLogs = inventoryInputLogs(type);
        int maxByCoins = inventoryCoins / type.getSawmillFee();
        if (maxByCoins <= 0) {
            prepareReturnToGeFromAuburnvale(false);
            return;
        }

        int emptySlots = Rs2Inventory.emptySlotCount();
        int additionalAffordable = Math.max(0, maxByCoins - inventoryLogs);
        int logsToWithdraw = Math.min(bankLogs, Math.min(emptySlots, additionalAffordable));

        if (logsToWithdraw > 0) {
            int expected = inventoryLogs + logsToWithdraw;
            traceAction("bankload:withdraw-logs qty=" + logsToWithdraw);
            boolean issued;
            if (logsToWithdraw == 1) {
                issued = Rs2Bank.withdrawOne(type.getInputLogId());
            } else if (logsToWithdraw >= bankLogs && bankLogs <= emptySlots) {
                issued = Rs2Bank.withdrawAll(type.getInputLogId());
            } else {
                issued = Rs2Bank.withdrawX(type.getInputLogId(), logsToWithdraw);
            }
            if (!issued) return;
            if (!sleepUntil(() -> inventoryInputLogs(type) >= expected, 4_000)) {
                Microbot.status = "Waiting for logs";
                return;
            }
        }

        if (!canProcessInventoryLogs()) {
            Microbot.status = "Waiting for a processable sawmill load";
            return;
        }

        if (!closeBankAndWait()) return;
        phase = Phase.WALK_TO_SAWMILL;
        if (walkTowardSawmill()) phase = Phase.MAKE_PLANKS;
    }

    private boolean sawmillInventoryOnly(PlankType type) {
        List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> items = Rs2Inventory.all();
        if (items == null) return false;
        for (var item : items) {
            if (item == null || item.getQuantity() <= 0) continue;
            int id = item.getId();
            if (id == ItemID.COINS || id == type.getInputLogId()) continue;
            boolean stamina = false;
            for (int sid : STAMINA_IDS) if (sid == id) { stamina = true; break; }
            if (config.useStaminaPotions() && stamina) continue;
            return false;
        }
        return true;
    }

    private boolean shouldSellPlanks() {
        PlankType type = config.plankType();

        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.walkToBankAndUseBank(BankLocation.AUBURNVALE)) {
                return false;
            }
            if (!sleepUntil(Rs2Bank::isOpen, 5_000)) {
                Microbot.status = "Waiting for Auburnvale bank widget";
                return false;
            }
        }

        int bankedPlanks = bankOutputPlanks(type);
        int inventoryPlanks = inventoryOutputPlanks(type);
        long totalPlanks = (long) bankedPlanks + inventoryPlanks;

        if (totalPlanks >= config.sellPlanksAt()) {
            Microbot.status = "Sell threshold reached: " + totalPlanks + "/"
                    + config.sellPlanksAt() + " planks";
            return true;
        }

        Microbot.status = "Keeping planks: " + totalPlanks + "/"
                + config.sellPlanksAt();
        return false;
    }

    private boolean shouldSellUnchargedRings() {
        if (!Rs2Bank.isOpen()) {
            return false;
        }

        int unchargedRings = Rs2Bank.count(ItemID.RING_OF_WEALTH);
        if (unchargedRings >= config.sellUnchargedRingsAt()) {
            Microbot.status = "Ring sell threshold reached: "
                    + unchargedRings + "/" + config.sellUnchargedRingsAt();
            return true;
        }

        return false;
    }

    private void prepareReturnToGeFromAuburnvale(boolean sellPlanks) {
        sellPlanksRequested = sellPlanksRequested || sellPlanks;

        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.walkToBankAndUseBank(BankLocation.AUBURNVALE)) return;
            if (!sleepUntil(Rs2Bank::isOpen, 5_000)) return;
        }
        if (!ensureBankWithdrawAsItem("RETURN_GE")) return;

        // Bank the sawmill load first and verify it has left inventory.
        if (!Rs2Inventory.isEmpty()) {
            traceAction("return-ge:deposit-all");
            if (!Rs2Bank.depositAll()) return;
            if (!sleepUntil(Rs2Inventory::isEmpty, 3_000)) {
                Microbot.status = "Waiting for return-to-GE deposit";
                return;
            }
        }

        int ringId = withdrawLowestChargedRing();
        if (ringId == -1 || firstInventoryItem(CHARGED_ROW_IDS) == -1) {
            Microbot.status = "No charged Ring of wealth available for GE return";
            return;
        }

        if (!closeBankAndWait()) return;
        Microbot.status = sellPlanksRequested
                ? "Ring of wealth -> GE sell/restock"
                : "Ring of wealth -> GE restock only";
        phase = Phase.TO_GE;
    }

    private void makePlanks() {
        PlankType type = config.plankType();
        int inputLogsBefore = inventoryInputLogs(type);

        if (inputLogsBefore <= 0) {
            phase = Phase.RETURN_TO_BANK;
            return;
        }

        int outputPlanksBefore = inventoryOutputPlanks(type);

        /*
         * IMPORTANT:
         * The production interface may already be open from the previous NPC click.
         * Always process an existing interface BEFORE attempting another NPC interaction.
         * This prevents the Sawmill Operator from being spam-clicked every script cycle.
         */
        if (!isSawmillInterfaceOpen()) {
            long now = System.currentTimeMillis();
            if (now - lastSawmillNpcClickAt < 3_000) {
                Microbot.status = "Waiting for sawmill interface";
                return;
            }

            Rs2NpcModel operator = findSawmillOperator();
            if (operator == null) {
                Microbot.status = "Finding Auburnvale Sawmill operator";
                Rs2Walker.walkStep(AUBURNVALE_SAWMILL_APPROACH, 4);
                return;
            }

            String action = resolvePlankAction(operator);
            if (action == null) {
                Microbot.status = "Sawmill Buy-plank action not found";
                return;
            }

            Microbot.status = "Opening sawmill";
            lastSawmillNpcClickAt = now;

            if (!operator.click(action)) {
                return;
            }

            if (!sleepUntil(this::isSawmillInterfaceOpen, 5_000)) {
                Microbot.status = "Waiting for sawmill widget/dialogue";
                return;
            }
        }

        // From this point onward the interface is already open.
        // Do NOT touch the NPC again unless the interface disappears.
        boolean selected = selectConfiguredPlank(type);

        if (!selected) {
            Microbot.status = "Sawmill open - waiting for " + type.getOutputPlankName();
            return;
        }

        boolean changed = sleepUntil(() ->
                        inventoryInputLogs(type) < inputLogsBefore
                                || inventoryOutputPlanks(type) > outputPlanksBefore,
                5_000);

        if (!changed) {
            Microbot.status = "Waiting for sawmill conversion";
            return;
        }

        int produced = Math.max(0, inventoryOutputPlanks(type) - outputPlanksBefore);
        if (produced == 0) {
            produced = Math.max(0, inputLogsBefore - inventoryInputLogs(type));
        }

        planksMade += produced;
        sawmillTrips++;
        phase = Phase.RETURN_TO_BANK;
    }

    private boolean isSawmillInterfaceOpen() {
        // RuneLite defines SKILLMULTI.UNIVERSE as component 270:0.
        // Microbot's Rs2Widget.isProductionWidgetOpen() checks the same component.
        return Rs2Widget.isProductionWidgetOpen()
                || Rs2Widget.isWidgetVisible(InterfaceID.Skillmulti.UNIVERSE);
    }

    private boolean isAllQuantitySelected(PlankType type) {
        int maxQuantity = inventoryInputLogs(type);
        if (maxQuantity <= 0) {
            return false;
        }

        int selectedQuantity = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getVarcIntValue(VarClientID.SKILLMULTI_QUANTITY)
        ).orElse(0);

        return selectedQuantity >= maxQuantity;
    }

    private boolean selectConfiguredPlank(PlankType type) {
        if (!isSawmillInterfaceOpen()) {
            Microbot.status = "Waiting for SKILLMULTI production interface";
            return false;
        }

        final int productionComponent = type.getProductionComponentId();

        if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(productionComponent), 3_000)) {
            Microbot.status = "Waiting for " + type.getInputLogName() + " production card";
            return false;
        }

        /*
         * Current RuneLite SKILLMULTI production cards:
         * 270:15 = A (regular)
         * 270:16 = B (oak)
         * 270:17 = C (teak)
         * 270:18 = D (mahogany)
         * 270:19 = E (camphor)
         * 270:20 = F (ironwood)
         * 270:21 = G (rosewood)
         *
         * OSRS stores the active Make-X amount in
         * VarClientID.SKILLMULTI_QUANTITY. The client script considers All
         * selected when that quantity is >= the interface's maximum amount.
         * For this sawmill the maximum is the number of selected input logs in
         * inventory, so only click ALL when the current quantity is lower.
         */
        if (!isAllQuantitySelected(type)) {
            if (!Rs2Widget.isWidgetVisible(InterfaceID.Skillmulti.ALL)) {
                Microbot.status = "Waiting for All quantity button";
                return false;
            }

            Microbot.status = "Selecting All quantity";
            if (!Rs2Widget.clickWidget(InterfaceID.Skillmulti.ALL)) {
                return false;
            }

            if (!sleepUntil(() -> isAllQuantitySelected(type), 1_500)) {
                Microbot.status = "Waiting for All quantity selection";
                return false;
            }
        }

        Microbot.status = "Selecting " + type.getInputLogName();
        return Rs2Widget.clickWidget(productionComponent);
    }

    private Rs2NpcModel findSawmillOperator() {
        Rs2NpcModel exact = Microbot.getRs2NpcCache().query()
                .withName("Sawmill Operator")
                .nearest();
        if (exact != null) {
            return exact;
        }

        return Microbot.getRs2NpcCache().query()
                .withName("Sawmill operator")
                .nearest();
    }

    /**
     * Avoids hard-coding "Buy-plank" vs "Buy planks": current NPC actions are inspected and
     * whichever action contains "plank" is used.
     */
    private String resolvePlankAction(Rs2NpcModel npc) {
        if (npc == null) return null;

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var composition = Microbot.getClient().getNpcDefinition(npc.getId());
            if (composition == null || composition.getActions() == null) {
                return null;
            }

            return Arrays.stream(composition.getActions())
                    .filter(a -> a != null && a.toLowerCase(Locale.ROOT).contains("plank"))
                    .findFirst()
                    .orElse(null);
        }).orElse(null);
    }

    private void drinkStaminaIfNeeded() {
        if (!config.useStaminaPotions()
                || Rs2Player.hasStaminaActive()
                || Rs2Player.getRunEnergy() > config.drinkStaminaBelow()) {
            return;
        }

        int potion = firstInventoryItem(STAMINA_IDS);
        if (potion == -1) {
            return;
        }

        int before = Rs2Player.getRunEnergy();
        Microbot.status = "Drinking lower-dose Stamina first";
        if (Rs2Inventory.interact(potion, "Drink")) {
            sleepUntil(() -> Rs2Player.hasStaminaActive() || Rs2Player.getRunEnergy() > before, 2_500);
        }
    }

    private int withdrawPreferredStamina() {
        int existing = firstInventoryItem(STAMINA_IDS);
        if (existing != -1) return existing;
        if (!Rs2Bank.isOpen() || !Rs2Bank.hasWithdrawAsItem()) return -1;

        for (int itemId : STAMINA_IDS) {
            if (exactBankCount(itemId) <= 0) continue;
            int before = Rs2Inventory.itemQuantity(itemId);
            traceAction("stamina:withdraw-one id=" + itemId);
            if (!Rs2Bank.withdrawOne(itemId)) continue;
            if (sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) > before, 2_500)) {
                return itemId;
            }
        }
        return -1;
    }

    private int withdrawLowestChargedRing() {
        int existing = firstInventoryItem(CHARGED_ROW_IDS);
        if (existing != -1) return existing;
        if (!Rs2Bank.isOpen() || !Rs2Bank.hasWithdrawAsItem()) return -1;

        for (int itemId : CHARGED_ROW_IDS) {
            if (exactBankCount(itemId) <= 0) continue;
            int before = Rs2Inventory.itemQuantity(itemId);
            traceAction("row:withdraw-one id=" + itemId);
            if (!Rs2Bank.withdrawOne(itemId)) continue;
            if (sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) > before, 2_500)) {
                return itemId;
            }
        }
        return -1;
    }

    private boolean closeBankAndWait() {
        if (!Rs2Bank.isOpen()) {
            return true;
        }
        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), 2_500)) {
            Microbot.status = "Waiting for bank to close";
            return false;
        }
        return true;
    }

    private boolean prepareGeBank() {
        if (!isAtGrandExchange()) return false;

        if (Rs2GrandExchange.isOpen()) {
            traceAction("ge-bank:close-ge");
            Rs2GrandExchange.closeExchange();
            if (!sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2_500)) {
                Microbot.status = "Waiting for GE to close";
                return false;
            }
        }

        if (!Rs2Bank.isOpen()) {
            traceAction("ge-bank:open-bank");
            if (!Rs2Bank.openBank()) return false;
            if (!sleepUntil(Rs2Bank::isOpen, 4_000)) {
                Microbot.status = "Waiting for bank widget";
                return false;
            }
        }

        if (Rs2Equipment.isWearing(ItemID.RING_OF_WEALTH)) {
            Rs2Equipment.unEquip(ItemID.RING_OF_WEALTH);
            if (!sleepUntil(() -> !Rs2Equipment.isWearing(ItemID.RING_OF_WEALTH), 2_000)) {
                Microbot.status = "Waiting to unequip depleted Ring of wealth";
                return false;
            }
        }

        if (!Rs2Inventory.isEmpty()) {
            traceAction("ge-bank:deposit-all");
            if (!Rs2Bank.depositAll()) return false;
            if (!sleepUntil(Rs2Inventory::isEmpty, 3_000)) {
                Microbot.status = "Waiting for GE bank deposit";
                return false;
            }
        }
        return true;
    }

    private boolean prepareGeBankPreservingInventory() {
        if (!isAtGrandExchange()) return false;
        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            if (!sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2_500)) return false;
        }
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank()) return false;
            if (!sleepUntil(Rs2Bank::isOpen, 4_000)) return false;
        }
        return true;
    }

    private boolean openGe() {
        if (!isAtGrandExchange()) {
            return false;
        }

        if (!closeBankAndWait()) {
            return false;
        }

        if (Rs2GrandExchange.isOpen()) {
            return true;
        }

        if (!Rs2GrandExchange.openExchange()) {
            return false;
        }

        if (!sleepUntil(Rs2GrandExchange::isOpen, 4_000)) {
            Microbot.status = "Waiting for Grand Exchange widget";
            return false;
        }

        return true;
    }

    private GrandExchangeSlots waitForOfferSlot(int itemId, boolean selling) {
        sleepUntil(() -> Rs2GrandExchange.findSlotForItem(itemId, selling) != null, 2_500);
        return Rs2GrandExchange.findSlotForItem(itemId, selling);
    }

    private int thresholdPrice(int itemId, boolean buying) {
        int base = marketPrice(itemId);
        if (base <= 0) {
            return -1;
        }

        int threshold = buying
                ? config.buyThresholdPercent()
                : config.sellThresholdPercent();

        double multiplier = buying
                ? 1.0 + threshold / 100.0
                : 1.0 - threshold / 100.0;

        long adjusted = buying
                ? (long) Math.ceil(base * multiplier)
                : (long) Math.floor(base * multiplier);

        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, adjusted));
    }

    private int marketPrice(int itemId) {
        long now = System.currentTimeMillis();
        PriceSnapshot cached = priceCache.get(itemId);
        if (cached != null && now - cached.timestamp < 30_000L) {
            return cached.price;
        }

        int price = Rs2GrandExchange.getPrice(itemId);
        if (price <= 0) {
            price = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                long value = Microbot.getItemManager().getItemPrice(itemId);
                return value <= 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, value);
            }).orElse(-1);
        }

        if (price > 0) {
            priceCache.put(itemId, new PriceSnapshot(price, now));
        }
        return price;
    }

    private String itemName(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemComposition composition = Microbot.getItemManager().getItemComposition(itemId);
            return composition == null ? "" : composition.getName();
        }).orElse("");
    }

    private int countBank(int[] ids) {
        int total = 0;
        for (int id : ids) {
            total += exactBankCount(id);
        }
        return total;
    }

    private int exactBankCount(int itemId) {
        if (!Rs2Bank.isOpen()) return 0;
        int total = 0;
        List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> items = Rs2Bank.bankItems();
        if (items == null) return 0;
        for (var item : items) {
            if (item != null && item.getId() == itemId) total += item.getQuantity();
        }
        return total;
    }

    private boolean ensureBankWithdrawAsItem(String context) {
        if (!Rs2Bank.isOpen()) return false;
        if (Rs2Bank.hasWithdrawAsItem()) return true;
        traceAction(context.toLowerCase(Locale.ROOT) + ":set-withdraw-items");
        if (!Rs2Bank.setWithdrawAsItem()) return false;
        if (!sleepUntil(Rs2Bank::hasWithdrawAsItem, 2_000)) {
            Microbot.status = "Waiting for bank item mode";
            return false;
        }
        return true;
    }

    private GeOfferSnapshot geOfferSnapshot(GrandExchangeSlots slot) {
        if (slot == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || slot.ordinal() >= offers.length) return null;
            GrandExchangeOffer offer = offers[slot.ordinal()];
            if (offer == null || offer.getItemId() == 0) return null;
            GrandExchangeOfferState state = offer.getState();
            boolean selling = state == GrandExchangeOfferState.SELLING
                    || state == GrandExchangeOfferState.SOLD
                    || state == GrandExchangeOfferState.CANCELLED_SELL;
            int price = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, offer.getPrice()));
            return new GeOfferSnapshot(offer.getItemId(), offer.getQuantitySold(),
                    offer.getTotalQuantity(), price, state, selling);
        }).orElse(null);
    }

    private void traceAction(String action) {
        lastDebugAction = action == null ? "" : action;
        debug("ACTION phase=%s %s", phase, lastDebugAction);
    }

    private void debug(String format, Object... args) {
        if (!config.debugLogging()) return;
        try {
            log.info("[FastPlanker DEBUG] " + String.format(Locale.ROOT, format, args));
        } catch (Exception ex) {
            log.info("[FastPlanker DEBUG] " + format);
        }
    }

    private void publishDebugSnapshot(boolean force) {
        if (!config.debugLogging()) return;
        long now = System.currentTimeMillis();
        long interval = Math.max(1, config.debugHeartbeatSeconds()) * 1_000L;
        if (!force && now - lastDebugSnapshotAt < interval) return;
        lastDebugSnapshotAt = now;
        PlankType type = config.plankType();
        debug("SNAP phase=%s status=\"%s\" action=\"%s\" pos=%s bank=%s ge=%s inv[logs=%d planks=%d coins=%d stamina=%d empty=%d] bank[logs=%d planks=%d coins=%d stamina=%d] sellCycle=%s",
                phase, String.valueOf(Microbot.status), lastDebugAction,
                String.valueOf(Rs2Player.getWorldLocation()), Rs2Bank.isOpen(), Rs2GrandExchange.isOpen(),
                inventoryInputLogs(type), inventoryOutputPlanks(type), Rs2Inventory.itemQuantity(ItemID.COINS),
                firstInventoryItem(STAMINA_IDS), Rs2Inventory.emptySlotCount(),
                Rs2Bank.isOpen() ? bankInputLogs(type) : -1,
                Rs2Bank.isOpen() ? bankOutputPlanks(type) : -1,
                Rs2Bank.isOpen() ? exactBankCount(ItemID.COINS) : -1,
                Rs2Bank.isOpen() ? countBank(STAMINA_IDS) : -1,
                sellPlanksRequested);
    }

    private int firstInventoryItem(int[] ids) {
        for (int id : ids) {
            if (Rs2Inventory.hasItem(id)) {
                return id;
            }
        }
        return -1;
    }

    private boolean walkTowardSawmill() {
        if (isNearAuburnvaleSawmill()) {
            Rs2Walker.setTarget(null, "fast-planker:sawmill-reached");
            Microbot.status = "Sawmill reached";
            return true;
        }

        Microbot.status = "Walking to Auburnvale sawmill";
        drinkStaminaIfNeeded();
        Rs2Walker.walkStep(AUBURNVALE_SAWMILL_APPROACH, 6);

        if (isNearAuburnvaleSawmill()) {
            Rs2Walker.setTarget(null, "fast-planker:sawmill-reached");
            Microbot.status = "Sawmill reached";
            return true;
        }
        return false;
    }

    private boolean walkTowardAuburnvaleBank() {
        if (isNearAuburnvaleBank()) {
            Rs2Walker.setTarget(null, "fast-planker:auburnvale-bank-reached");
            Microbot.status = "Auburnvale bank reached";
            return true;
        }

        Microbot.status = "Returning to Auburnvale bank";
        drinkStaminaIfNeeded();
        Rs2Walker.walkStep(AUBURNVALE_BANK, 8);

        if (isNearAuburnvaleBank()) {
            Rs2Walker.setTarget(null, "fast-planker:auburnvale-bank-reached");
            Microbot.status = "Auburnvale bank reached";
            return true;
        }
        return false;
    }

    private boolean isNearAuburnvaleSawmill() {
        WorldPoint here = Rs2Player.getWorldLocation();
        return here != null
                && here.getPlane() == AUBURNVALE_SAWMILL_APPROACH.getPlane()
                && here.distanceTo2D(AUBURNVALE_SAWMILL_APPROACH) <= 8;
    }

    private boolean travelToAuburnvaleViaQuetzal() {
        if (isNearAuburnvaleQuetzalLanding() || isNearAuburnvaleBank()) {
            return true;
        }

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) {
            return false;
        }

        // Short safe approach from the Civitas tablet landing to Renu.
        if (here.getPlane() != CIVITAS_QUETZAL.getPlane()
                || here.distanceTo2D(CIVITAS_QUETZAL) > 6) {
            Microbot.status = "Walking to Civitas Quetzal";
            drinkStaminaIfNeeded();
            Rs2Walker.walkStep(CIVITAS_QUETZAL, 4);
            return false;
        }

        // If the map is already open, never re-click Renu.
        if (!isQuetzalMapOpen()) {
            Microbot.status = "Opening Renu Quetzal map";

            /*
             * Do not bind Rs2Npc.getNpc(...) to Rs2NpcModel here.
             * Microbot 2.6.26 has two Rs2NpcModel classes:
             *   api.npc.models.Rs2NpcModel  (used by the NPC cache)
             *   util.npc.Rs2NpcModel        (returned by Rs2Npc.getNpc)
             *
             * The ID overload avoids that type collision and is the supported
             * Rs2Npc interaction path for this fixed NPC.
             */
            if (!Rs2Npc.interact(NpcID.QUETZAL_CHILD_GREEN, "Travel")) {
                Microbot.status = "Waiting for Renu";
                return false;
            }

            if (!sleepUntil(this::isQuetzalMapOpen, 4_000)) {
                Microbot.status = "Waiting for Quetzal map";
                return false;
            }
        }

        Widget auburnvale = findQuetzalDestination("Auburnvale");
        if (auburnvale == null) {
            Microbot.status = "Waiting for Auburnvale Quetzal option";
            return false;
        }

        Microbot.status = "Quetzal -> Auburnvale";
        if (!Rs2Widget.clickWidget(auburnvale)) {
            return false;
        }

        // Event/state wait: only continue once the player has actually landed.
        return sleepUntil(this::isNearAuburnvaleQuetzalLanding, 10_000);
    }

    private boolean isQuetzalMapOpen() {
        return Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.UNIVERSE)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.MAP)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.ICONS)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.CONTENTS);
    }

    private Widget findQuetzalDestination(String destination) {
        int[] roots = {
                InterfaceID.QuetzalMenu.ICONS,
                InterfaceID.QuetzalMenu.MAP,
                InterfaceID.QuetzalMenu.SCROLL,
                InterfaceID.QuetzalMenu.CONTENTS,
                InterfaceID.QuetzalMenu.UNIVERSE
        };

        for (int rootId : roots) {
            Widget root = Rs2Widget.getWidget(rootId);
            if (root == null || Rs2Widget.isHidden(rootId)) {
                continue;
            }

            Widget match = Rs2Widget.findWidget(destination, List.of(root), true);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private boolean isNearAuburnvaleQuetzalLanding() {
        WorldPoint here = Rs2Player.getWorldLocation();
        return here != null
                && here.getPlane() == AUBURNVALE_QUETZAL_LANDING.getPlane()
                && here.distanceTo2D(AUBURNVALE_QUETZAL_LANDING) <= 6;
    }

    private boolean isNearAuburnvaleBank() {
        WorldPoint here = Rs2Player.getWorldLocation();
        return here != null
                && here.getPlane() == AUBURNVALE_BANK.getPlane()
                && here.distanceTo2D(AUBURNVALE_BANK) <= 10;
    }

    private boolean isAtGrandExchange() {
        WorldPoint here = Rs2Player.getWorldLocation();
        return here != null
                && here.getPlane() == GE_POINT.getPlane()
                && here.distanceTo2D(GE_POINT) <= 30;
    }

    private int offerWaitMillis() {
        return Math.max(5, config.offerWaitSeconds()) * 1_000;
    }

    private void stop(String reason) {
        Microbot.status = "Stopped: " + reason;
        log.warn(reason);
        phase = Phase.STOPPED;
    }

    public String getPhaseName() {
        return phase.name();
    }

    public long getRuntimeMillis() {
        return startedAt <= 0 ? 0 : System.currentTimeMillis() - startedAt;
    }

    public long getPlanksMade() {
        return planksMade;
    }

    public long getSawmillTrips() {
        return sawmillTrips;
    }

    public long getGeCycles() {
        return geCycles;
    }

    public long getPlanksPerHour() {
        long runtime = getRuntimeMillis();
        return runtime <= 0 ? 0 : Math.round(planksMade * 3_600_000.0 / runtime);
    }

    public long getTimeUntilWorldHopSeconds() {
        return worldHopper.getTimeUntilNextHopSeconds();
    }

    @Override
    public void shutdown() {
        worldHopper.shutdown();
        super.shutdown();
    }

    private static final class SellOfferWaitState {
        private final int slotOrdinal;
        private final int price;
        private final int totalQuantity;
        private int quantitySold;
        private long lastActivityAt;

        private SellOfferWaitState(int slotOrdinal, int price, int totalQuantity,
                                   int quantitySold, long lastActivityAt) {
            this.slotOrdinal = slotOrdinal;
            this.price = price;
            this.totalQuantity = totalQuantity;
            this.quantitySold = quantitySold;
            this.lastActivityAt = lastActivityAt;
        }
    }

    private static final class GeOfferSnapshot {
        private final int itemId;
        private final int quantitySold;
        private final int totalQuantity;
        private final int price;
        private final GrandExchangeOfferState state;
        private final boolean selling;

        private GeOfferSnapshot(int itemId, int quantitySold, int totalQuantity,
                                int price, GrandExchangeOfferState state, boolean selling) {
            this.itemId = itemId;
            this.quantitySold = quantitySold;
            this.totalQuantity = totalQuantity;
            this.price = price;
            this.state = state;
            this.selling = selling;
        }
    }

    private static final class PriceSnapshot {
        private final int price;
        private final long timestamp;

        private PriceSnapshot(int price, long timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
    }

    private enum Phase {
        TO_GE,
        SELL_OUTPUTS,
        BUY_INPUTS,
        PREPARE_AUBURNVALE,
        TELEPORT_CIVITAS,
        WALK_TO_AUBURNVALE,
        BANK_LOAD,
        WALK_TO_SAWMILL,
        MAKE_PLANKS,
        RETURN_TO_BANK,
        STOPPED
    }
}
