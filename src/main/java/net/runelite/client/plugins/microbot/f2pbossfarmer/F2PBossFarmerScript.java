package net.runelite.client.plugins.microbot.f2pbossfarmer;

import net.runelite.api.Actor;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.f2pbossfarmer.enums.BossMode;
import net.runelite.client.plugins.microbot.f2pbossfarmer.enums.BossTarget;
import net.runelite.client.plugins.microbot.f2pbossfarmer.enums.FarmerState;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class F2PBossFarmerScript extends Script {

    private static final int[] OBOR_ENTRANCE_GATE_IDS = {29486, 29487};

    // User-confirmed with Object Examiner: both Obor entry Rocks use ID 29491.
    private static final int OBOR_ENTRY_ROCKS_ID = 29491;

    // One game-tick settle window after entering the Obor Rocks area.
    private static final long OBOR_ROCKS_SETTLE_MS = 600L;
    // Exact Microbot 2.6.19 transport definitions for the Brass-key shortcut:
    // Door 1804: 3115,3449 <-> 3115,3450, requires Brass key (item 983)
    // Ladder 17384: 3115,3452 -> 3115,9852, action "Climb-down"
    private static final int OBOR_SHED_DOOR_ID = 1804;
    private static final int OBOR_SHED_LADDER_ID = 17384;
    private static final WorldPoint OBOR_SHED_DOOR_OUTSIDE = new WorldPoint(3115, 3449, 0);
    private static final WorldPoint OBOR_SHED_LADDER = new WorldPoint(3115, 3452, 0);
    private static final WorldPoint OBOR_DUNGEON_SHORTCUT_DEST = new WorldPoint(3115, 9852, 0);

    /*
     * User-confirmed Obor gate interaction area:
     * Area(3093, 9835, 3096, 9830)
     *
     * RuneLite WorldArea uses south-west X/Y plus width/height, so the
     * inclusive rectangle X=3093..3096 and Y=9830..9835 is 4 x 6 tiles.
     *
     * The single point below is ONLY a walking target inside the area. It is
     * not used as an interaction trigger.
     */
    private static final WorldArea OBOR_GATE_AREA = new WorldArea(3093, 9830, 4, 6, 0);
    private static final WorldPoint OBOR_GATE_AREA_WALK_TARGET = new WorldPoint(3095, 9833, 0);

    /*
     * User-confirmed Obor safe-zone rocks area:
     * Area(3090, 9815, 3093, 9812)
     *
     * Inclusive rectangle:
     * X = 3090..3093
     * Y = 9812..9815
     */
    private static final WorldArea OBOR_ROCKS_AREA = new WorldArea(3090, 9812, 4, 4, 0);
    private static final WorldPoint OBOR_ROCKS_AREA_WALK_TARGET = new WorldPoint(3091, 9814, 0);

    /*
     * User-confirmed nearest Prayer altar area:
     * Area(3259, 3484, 3252, 3471)
     *
     * Inclusive rectangle:
     * X = 3252..3259
     * Y = 3471..3484
     * Plane = 0
     *
     * The walk target is only a point inside the area. Once inside, the actual
     * scenery object named "Altar" is located and interacted with via Pray-at.
     */
    private static final WorldArea PRAYER_ALTAR_AREA =
            new WorldArea(3252, 3471, 8, 14, 0);
    private static final WorldPoint PRAYER_ALTAR_AREA_WALK_TARGET =
            new WorldPoint(3255, 3478, 0);

    private static final int BRYOPHYTA_CHEST_ID = 56378;
    private static final int BRYOPHYTA_EXIT_ROCK_PILE_ID = 32535;

    @Inject
    private F2PBossFarmerConfig config;

    private volatile FarmerState state = FarmerState.STARTING;
    private volatile BossTarget activeTarget = BossTarget.OBOR;
    private BossTarget lastCompletedBoss;

    private boolean bossSeenThisFight;
    /**
     * Obor can be visible from outside the arena through the entrance gate.
     * Therefore NPC visibility must never be used as proof that the rocks
     * transition has completed.
     */
    private boolean oborArenaEntered;
    private boolean oborGateTransitionPending;
    private boolean oborRocksTransitionPending;
    private long oborRocksAreaEnteredAt;
    private boolean bankLoadoutPrepared;
    private boolean bankInventoryCleared;
    private boolean emergencyRecoveryRequested;
    private int pendingChestKeyCount = -1;

    private int sessionGiantKeys;
    private int sessionMossyKeys;
    private int oborChests;
    private int bryophytaChests;

    public boolean run(F2PBossFarmerConfig config) {
        this.config = config;
        resetRuntime();

        Microbot.enableAutoRunOn = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }

                if (!super.run()) {
                    return;
                }

                tick();
            } catch (Exception ex) {
                state = FarmerState.ERROR;
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);

        return true;
    }

    private void resetRuntime() {
        state = FarmerState.STARTING;
        activeTarget = config.mode() == BossMode.BRYOPHYTA_ONLY
                ? BossTarget.BRYOPHYTA
                : config.autoStartBoss();
        lastCompletedBoss = null;
        bossSeenThisFight = false;
        oborArenaEntered = false;
        oborGateTransitionPending = false;
        oborRocksTransitionPending = false;
        oborRocksAreaEnteredAt = 0L;
        bankLoadoutPrepared = false;
        bankInventoryCleared = false;
        emergencyRecoveryRequested = false;
        pendingChestKeyCount = -1;
        sessionGiantKeys = 0;
        sessionMossyKeys = 0;
        oborChests = 0;
        bryophytaChests = 0;
    }

    private void tick() {
        updateSessionKeyCounters();

        /*
         * Location recovery has priority over STARTING/banking/key logic.
         *
         * This is especially important when the plugin is enabled while already
         * inside Obor's instanced safe-zone. Template coordinates let us recover
         * even though the raw Actor world coordinate is in the 13xxx/9xxx
         * instance-space range.
         */
        boolean inOborGateArea = isPlayerInsideOborGateArea();
        boolean inOborRocksArea = isPlayerInsideOborRocksArea();

        if (!oborArenaEntered
                && (inOborGateArea || inOborRocksArea)
                && state != FarmerState.FIGHTING_BOSS
                && state != FarmerState.OPENING_CHEST
                && state != FarmerState.EXITING_BOSS) {
            activeTarget = BossTarget.OBOR;
            state = FarmerState.ENTERING_BOSS;

            if (inOborRocksArea) {
                // We are already past the Gate/Door transition.
                oborGateTransitionPending = false;
                if (oborRocksAreaEnteredAt == 0L) {
                    oborRocksAreaEnteredAt = System.currentTimeMillis();
                }
                Microbot.status = "Obor: recovered inside rocks area";
            } else {
                Microbot.status = "Obor: recovered inside gate area";
            }
        }

        /*
         * Matching boss keys always take priority over normal giant farming.
         *
         * This also repairs stale states after a transport. In particular,
         * after descending the Brass-key ladder the script may still be in
         * TRAVELLING_TO_GIANTS/FARMING_KEYS from the previous tick even though
         * a Giant key is already present. Force the Obor travel state before
         * any giant-search logic can run.
         */
        if (activeTarget == BossTarget.OBOR
                && Rs2Inventory.hasItem("Giant key")
                && (state == FarmerState.TRAVELLING_TO_GIANTS
                || state == FarmerState.FARMING_KEYS)) {
            Microbot.status = "Obor: Giant key ready - travelling to lair";
            state = FarmerState.TRAVELLING_TO_BOSS;
        } else if (activeTarget == BossTarget.BRYOPHYTA
                && Rs2Inventory.hasItem("Mossy key")
                && (state == FarmerState.TRAVELLING_TO_GIANTS
                || state == FarmerState.FARMING_KEYS)) {
            Microbot.status = "Bryophyta: Mossy key ready - travelling to lair";
            state = FarmerState.TRAVELLING_TO_BOSS;
        }

        /*
         * Bryophyta visibility is enough to recover into the fight state.
         *
         * Obor is different: he can be visible from OUTSIDE the arena through
         * the gate / safe-zone entrance. Never let NPC visibility skip the
         * mandatory Rocks -> Climb-down transition.
         */
        if (state != FarmerState.OPENING_CHEST
                && state != FarmerState.EXITING_BOSS
                && findBoss(activeTarget) != null
                && (activeTarget != BossTarget.OBOR || oborArenaEntered)) {
            state = FarmerState.FIGHTING_BOSS;
        }

        switch (state) {
            case STARTING:
                activeTarget = selectTargetFromCurrentInventory();

                if (hasRequiredLoadout(activeTarget)) {
                    Microbot.status = "Loadout ready - skipping bank";
                    routeCurrentLoadout(activeTarget);
                } else {
                    requestBanking("Loadout incomplete - banking");
                }
                break;

            case BANKING:
                handleBanking();
                break;

            case RESTORING_PRAYER:
                handlePrayerRestore();
                break;

            case TRAVELLING_TO_GIANTS:
                handleTravelToGiants();
                break;

            case FARMING_KEYS:
                handleKeyFarming();
                break;

            case TRAVELLING_TO_BOSS:
                handleTravelToBoss();
                break;

            case ENTERING_BOSS:
                handleBossEntry();
                break;

            case FIGHTING_BOSS:
                handleBossFight();
                break;

            case OPENING_CHEST:
                handleChest();
                break;

            case EXITING_BOSS:
                handleBossExit();
                break;

            case ERROR:
                break;
        }
    }

    /**
     * Selects a target without opening the bank.
     *
     * In AUTO mode an already-carried matching key takes priority. If there is
     * no carried boss key, keep the configured/rotation target and allow normal
     * giant key farming without forcing a bank visit.
     */
    private BossTarget selectTargetFromCurrentInventory() {
        BossMode mode = config.mode();

        if (mode == BossMode.OBOR_ONLY) {
            return BossTarget.OBOR;
        }

        if (mode == BossMode.BRYOPHYTA_ONLY) {
            return BossTarget.BRYOPHYTA;
        }

        int giantKeys = Rs2Inventory.itemQuantity(BossTarget.OBOR.getKeyName());
        int mossyKeys = Rs2Inventory.itemQuantity(BossTarget.BRYOPHYTA.getKeyName());

        if (giantKeys > 0 && mossyKeys > 0) {
            if (lastCompletedBoss == BossTarget.OBOR) {
                return BossTarget.BRYOPHYTA;
            }

            if (lastCompletedBoss == BossTarget.BRYOPHYTA) {
                return BossTarget.OBOR;
            }

            return config.autoStartBoss();
        }

        if (giantKeys > 0) {
            return BossTarget.OBOR;
        }

        if (mossyKeys > 0) {
            return BossTarget.BRYOPHYTA;
        }

        if (lastCompletedBoss != null && config.alternateAfterChest()) {
            return lastCompletedBoss.opposite();
        }

        return config.autoStartBoss();
    }

    /**
     * Returns true when the player can continue the selected route using the
     * inventory/equipment already carried.
     *
     * This intentionally does NOT require free inventory slots. A low slot
     * count alone is not a reason to destroy/rebuild an otherwise valid loadout.
     */
    private boolean hasRequiredLoadout(BossTarget target) {
        if (target == null) {
            return false;
        }

        String food = clean(config.foodName());
        if (food.isEmpty()
                || Rs2Inventory.itemQuantity(food) <= config.bankAtFood()) {
            return false;
        }

        String combatWeapon = clean(config.combatWeapon());
        if (!combatWeapon.isEmpty()
                && !Rs2Equipment.isWearing(combatWeapon)
                && !Rs2Inventory.hasItem(combatWeapon)) {
            return false;
        }

        if (target == BossTarget.OBOR) {
            /*
             * Brass key is only needed to reach the Edgeville Dungeon shortcut.
             * Once already underground, lack of a Brass key must not send the
             * player back to a bank.
             */
            if (!isUnderground() && !Rs2Inventory.hasItem("Brass key")) {
                return false;
            }

            return true;
        }

        String axe = clean(config.growthlingAxe());
        if (!axe.isEmpty()
                && !Rs2Equipment.isWearing(axe)
                && !Rs2Inventory.hasItem(axe)) {
            return false;
        }

        /*
         * Knife is a route requirement for entering Varrock Sewers. Once the
         * player is already underground it is not a reason to bank.
         */
        if (config.bringKnife()
                && !isUnderground()
                && !Rs2Inventory.hasItem("Knife")) {
            return false;
        }

        return true;
    }

    /**
     * Continues immediately with the loadout already on the player.
     */
    private void routeCurrentLoadout(BossTarget target) {
        activeTarget = target;
        equipCombatWeaponIfAvailable();

        if (Rs2Inventory.itemQuantity(target.getKeyName()) > 0) {
            Microbot.status = target == BossTarget.OBOR
                    ? "Loadout ready - travelling to Obor"
                    : "Loadout ready - travelling to Bryophyta";
            state = FarmerState.TRAVELLING_TO_BOSS;
        } else {
            Microbot.status = target == BossTarget.OBOR
                    ? "Loadout ready - farming Giant key"
                    : "Loadout ready - farming Mossy key";
            state = FarmerState.TRAVELLING_TO_GIANTS;
        }
    }

    private void handleBanking() {
        disableProtectionPrayer();

        /*
         * All supply preparation is complete. Close the bank and do not resume
         * the route until Prayer has also been restored when needed.
         */
        if (bankLoadoutPrepared) {
            if (Rs2Bank.isOpen()) {
                Microbot.status = "Bank recovery complete - closing bank";
                Rs2Bank.closeBank();
                return;
            }

            bankLoadoutPrepared = false;
            bankInventoryCleared = false;
            equipCombatWeaponIfAvailable();

            if (needsPrayerRestore()) {
                Microbot.status = "Bank healed - restoring Prayer";
                state = FarmerState.RESTORING_PRAYER;
                return;
            }

            emergencyRecoveryRequested = false;
            routeCurrentLoadout(activeTarget);
            return;
        }

        Microbot.status = emergencyRecoveryRequested
                ? "Emergency recovery - banking"
                : "Banking for missing/low requirements";

        if (!Rs2Bank.isOpen()) {
            if (Rs2Bank.walkToBank()) {
                Rs2Bank.openBank();
            }
            return;
        }

        /*
         * Deposit the carried inventory exactly once. Multi-tick healing below
         * must not cause the newly withdrawn food/supplies to be deposited again.
         */
        if (!bankInventoryCleared) {
            if (!Rs2Inventory.isEmpty()) {
                Rs2Bank.depositAll();
                return;
            }

            bankInventoryCleared = true;
            activeTarget = selectTargetAtBank();
        }

        /*
         * Keep the complete required loadout available while healing. Since
         * withdrawDeficit is used, every food eaten at the bank is topped back
         * up on the following 50 ms FSM cycle.
         */
        if (!withdrawSupplies(activeTarget)) {
            return;
        }

        /*
         * Heal to the real Hitpoints level before leaving the bank.
         */
        if (!isFullHealth()) {
            String food = clean(config.foodName());

            if (!Rs2Inventory.hasItem(food)) {
                if (!Rs2Bank.withdrawDeficit(food, 1)) {
                    Microbot.showMessage("Unable to withdraw food for bank healing: " + food);
                    state = FarmerState.ERROR;
                }
                return;
            }

            Microbot.status = "Healing to full at bank";
            Rs2Inventory.interact(food, "Eat");
            return;
        }

        /*
         * Healing consumed food, so top the configured loadout back up to its
         * requested amount before handling boss keys and closing the bank.
         */
        if (!withdrawSupplies(activeTarget)) {
            return;
        }

        int availableKeys = bankQuantity(activeTarget.getKeyName())
                + Rs2Inventory.itemQuantity(activeTarget.getKeyName());

        int keysToTake = Math.min(config.keysBeforeBoss(), availableKeys);

        if (keysToTake > 0
                && !Rs2Bank.withdrawDeficit(activeTarget.getKeyName(), keysToTake, true)) {
            Microbot.showMessage("Unable to withdraw " + activeTarget.getKeyName());
            state = FarmerState.ERROR;
            return;
        }

        bankLoadoutPrepared = true;
        Microbot.status = "HP full - loadout ready";
        Rs2Bank.closeBank();
    }

    private BossTarget selectTargetAtBank() {
        BossMode mode = config.mode();

        if (mode == BossMode.OBOR_ONLY) {
            return BossTarget.OBOR;
        }

        if (mode == BossMode.BRYOPHYTA_ONLY) {
            return BossTarget.BRYOPHYTA;
        }

        int giantKeys = bankQuantity(BossTarget.OBOR.getKeyName());
        int mossyKeys = bankQuantity(BossTarget.BRYOPHYTA.getKeyName());

        if (giantKeys > 0 && mossyKeys > 0) {
            if (lastCompletedBoss == BossTarget.OBOR) {
                return BossTarget.BRYOPHYTA;
            }
            if (lastCompletedBoss == BossTarget.BRYOPHYTA) {
                return BossTarget.OBOR;
            }
            return config.autoStartBoss();
        }

        if (giantKeys > 0) {
            return BossTarget.OBOR;
        }

        if (mossyKeys > 0) {
            return BossTarget.BRYOPHYTA;
        }

        if (lastCompletedBoss != null && config.alternateAfterChest()) {
            return lastCompletedBoss.opposite();
        }

        return config.autoStartBoss();
    }

    private boolean withdrawSupplies(BossTarget target) {
        String food = clean(config.foodName());
        if (food.isEmpty()) {
            Microbot.showMessage("Food name is empty.");
            state = FarmerState.ERROR;
            return false;
        }

        if (!Rs2Bank.withdrawDeficit(food, config.foodAmount())) {
            Microbot.showMessage("Missing food in bank: " + food);
            state = FarmerState.ERROR;
            return false;
        }

        String combatWeapon = clean(config.combatWeapon());
        if (!combatWeapon.isEmpty()
                && !Rs2Equipment.isWearing(combatWeapon)
                && !Rs2Inventory.hasItem(combatWeapon)
                && !Rs2Bank.withdrawDeficit(combatWeapon, 1)) {
            Microbot.showMessage("Missing combat weapon in bank: " + combatWeapon);
            state = FarmerState.ERROR;
            return false;
        }

        if (target == BossTarget.OBOR) {
            if (!Rs2Inventory.hasItem("Brass key")
                    && !Rs2Bank.withdrawDeficit("Brass key", 1, true)) {
                Microbot.showMessage("Missing Brass key in bank for Hill Giants / Obor route.");
                state = FarmerState.ERROR;
                return false;
            }
        }

        if (target == BossTarget.BRYOPHYTA) {
            String axe = clean(config.growthlingAxe());
            if (!axe.isEmpty()
                    && !Rs2Equipment.isWearing(axe)
                    && !Rs2Inventory.hasItem(axe)
                    && !Rs2Bank.withdrawDeficit(axe, 1)) {
                Microbot.showMessage("Missing Growthling axe in bank: " + axe);
                state = FarmerState.ERROR;
                return false;
            }

            if (config.bringKnife()
                    && !Rs2Inventory.hasItem("Knife")
                    && !Rs2Bank.withdrawDeficit("Knife", 1, true)) {
                Microbot.showMessage("Missing Knife in bank for Varrock Sewers route.");
                state = FarmerState.ERROR;
                return false;
            }
        }

        return true;
    }

    private void handleTravelToGiants() {
        if (needsBank()) {
            requestBanking("Loadout requires bank");
            return;
        }

        if (Rs2Inventory.itemQuantity(activeTarget.getKeyName()) > 0) {
            Microbot.status = activeTarget == BossTarget.OBOR
                    ? "Obor: Giant key ready - travelling to lair"
                    : "Bryophyta: Mossy key ready - travelling to lair";
            state = FarmerState.TRAVELLING_TO_BOSS;
            return;
        }

        if (activeTarget == BossTarget.OBOR && !isUnderground()) {
            handleOborDungeonShortcut();
            return;
        }

        WorldPoint anchor = giantAnchor(activeTarget);
        Rs2Walker.walkTo(anchor, 8);

        if (findGiant(activeTarget) != null) {
            state = FarmerState.FARMING_KEYS;
        }
    }

    private void handleKeyFarming() {
        if (needsBank()) {
            requestBanking("Loadout requires bank");
            return;
        }

        if (Rs2Inventory.itemQuantity(activeTarget.getKeyName()) >= config.keysBeforeBoss()) {
            Microbot.status = activeTarget == BossTarget.OBOR
                    ? "Obor: Giant key ready - travelling to lair"
                    : "Bryophyta: Mossy key ready - travelling to lair";
            state = FarmerState.TRAVELLING_TO_BOSS;
            return;
        }

        if (tryLootMatchingKey(activeTarget)) {
            return;
        }

        if (getPlayerWorldLocation() == null) {
            return;
        }

        if (Rs2Player.isInteracting()) {
            eatIfNeeded();
            return;
        }

        Rs2NpcModel giant = findGiant(activeTarget);
        if (giant == null) {
            state = FarmerState.TRAVELLING_TO_GIANTS;
            return;
        }

        eatIfNeeded();

        if (!Rs2Player.isAnimating()) {
            giant.click("Attack");
        }
    }

    private void handleTravelToBoss() {
        if (Rs2Inventory.itemQuantity(activeTarget.getKeyName()) <= 0) {
            state = FarmerState.TRAVELLING_TO_GIANTS;
            return;
        }

        if (needsBank()) {
            requestBanking("Loadout requires bank");
            return;
        }

        if (activeTarget == BossTarget.OBOR) {
            if (!isUnderground()) {
                handleOborDungeonShortcut();
                return;
            }

            /*
             * Giant key ready: use the user-confirmed rectangular gate area,
             * not one/two exact approach tiles.
             *
             * As soon as the player enters OBOR_GATE_AREA, stop walking and
             * immediately run Gate -> Open. Gate visibility is intentionally
             * not a prerequisite for entering the interaction state.
             */
            if (Rs2Inventory.hasItem("Giant key")) {
                WorldPoint playerLocation = getPlayerTemplateWorldLocation();

                if (playerLocation == null) {
                    return;
                }

                if (OBOR_GATE_AREA.contains(playerLocation)) {
                    Microbot.status = "Obor: inside gate area - opening lair";
                    Rs2Walker.setTarget(null);
                    state = FarmerState.ENTERING_BOSS;

                    // Zero-delay handoff: attempt Open in this same FSM cycle.
                    tryOpenOborGate();
                    return;
                }

                Microbot.status = "Obor: walking into gate area";
                Rs2Walker.walkTo(OBOR_GATE_AREA_WALK_TARGET, 1);
                return;
            }

            Rs2Walker.walkTo(giantAnchor(BossTarget.OBOR), 8);

            if (Microbot.getRs2TileObjectCache().query()
                    .withIds(OBOR_ENTRANCE_GATE_IDS)
                    .nearest() != null) {
                state = FarmerState.ENTERING_BOSS;
            }
            return;
        }

        Rs2Walker.walkTo(giantAnchor(BossTarget.BRYOPHYTA), 8);

        if (findGiant(BossTarget.BRYOPHYTA) != null
                || Microbot.getRs2TileObjectCache().query()
                .withName("Gate")
                .nearest(25) != null) {
            state = FarmerState.ENTERING_BOSS;
        }
    }

    /**
     * Explicitly traverses the Brass-key shortcut into the Edgeville Dungeon.
     *
     * The Hill Giant/Obor anchors are underground. Walking directly to those
     * coordinates can stall on the surface because the shortcut contains a
     * locked shed door followed by a ladder transport. Keep this transition
     * deterministic instead of relying on Rs2Walker to infer a keyed door.
     */
    private void handleOborDungeonShortcut() {
        WorldPoint playerLocation = getPlayerWorldLocation();
        if (playerLocation == null) {
            return;
        }

        if (isUnderground()) {
            if (Rs2Inventory.hasItem("Giant key")) {
                Microbot.status = "Obor: underground - walking to lair gate";
                state = FarmerState.TRAVELLING_TO_BOSS;
            } else {
                Microbot.status = "Obor: underground - farming Giant key";
                state = FarmerState.TRAVELLING_TO_GIANTS;
            }
            return;
        }

        if (!Rs2Inventory.hasItem("Brass key")) {
            Microbot.showMessage("Brass key missing while travelling to Hill Giants / Obor; returning to bank.");
            requestBanking("Missing Brass key - banking");
            return;
        }

        /*
         * Do not use a generic "Ladder" query here.
         *
         * Microbot 2.6.19 transports.tsv explicitly defines:
         *   Door   1804  at 3115,3449 -> 3115,3450 (Brass key required)
         *   Ladder 17384 at 3115,3452 -> 3115,9852 (Climb-down)
         *
         * The previous generic query could leave the player standing inside
         * the shed without ever dispatching the correct ladder interaction.
         */

        // If we are already inside the small shed (as in the reported stall),
        // the ladder is the only transition that matters. Never reopen the door.
        if (isInsideOborShortcutShed(playerLocation)) {
            Microbot.status = "Obor: descending Brass-key ladder";

            if (playerLocation.distanceTo(OBOR_SHED_LADDER) > 2) {
                Rs2Walker.walkTo(OBOR_SHED_LADDER, 1);
                return;
            }

            if (!canIssueImmediateInteraction()) {
                return;
            }

            boolean climbedDown = Microbot.getRs2TileObjectCache().query()
                    .withIds(OBOR_SHED_LADDER_ID)
                    .interact("Climb-down", 6);

            if (climbedDown) {
                /*
                 * Do not block. The next 50 ms FSM cycle observes the changed
                 * world Y and immediately continues. If the action did not
                 * register, it is retried as soon as the player is idle again.
                 */
                Microbot.status = "Obor: ladder clicked - awaiting scene change";
            }
            return;
        }

        // Approach the exact outside tile used by Microbot's transport table.
        if (playerLocation.distanceTo(OBOR_SHED_DOOR_OUTSIDE) > 3) {
            Microbot.status = "Obor: walking to Brass-key shed";
            Rs2Walker.walkTo(OBOR_SHED_DOOR_OUTSIDE, 1);
            return;
        }

        /*
         * If the ladder is already visible and the closed door object has
         * disappeared, the door is open. Walk through it to the exact ladder
         * tile instead of trying to issue another Open action.
         */
        boolean closedDoorVisible = Microbot.getRs2TileObjectCache().query()
                .withIds(OBOR_SHED_DOOR_ID)
                .nearest(6) != null;

        if (!closedDoorVisible) {
            Microbot.status = "Obor: entering Brass-key shed";
            Rs2Walker.walkTo(OBOR_SHED_LADDER, 1);
            return;
        }

        if (!canIssueImmediateInteraction()) {
            return;
        }

        Microbot.status = "Obor: opening Brass-key shed";
        boolean openedDoor = Microbot.getRs2TileObjectCache().query()
                .withIds(OBOR_SHED_DOOR_ID)
                .interact("Open", 6);

        if (openedDoor) {
            Microbot.status = "Obor: shed door clicked";
        }
    }

    private boolean isInsideOborShortcutShed(WorldPoint point) {
        if (point == null || point.getPlane() != 0) {
            return false;
        }

        // Door transport crosses from y=3449 (outside) to y=3450 (inside).
        // Keep the bounds deliberately tight to this small building.
        return point.getX() >= 3112
                && point.getX() <= 3118
                && point.getY() >= 3450
                && point.getY() <= 3457;
    }

    /**
     * RuneLite Actor#getWorldLocation() is client-thread restricted in the
     * current Microbot client. This script runs on F2PBossFarmerScript-* worker
     * threads, so always snapshot the location through ClientThread#invoke(Supplier).
     */
    private WorldPoint getPlayerWorldLocation() {
        try {
            return Microbot.getClientThread().invoke(() -> {
                if (Microbot.getClient().getLocalPlayer() == null) {
                    return null;
                }
                return Microbot.getClient().getLocalPlayer().getWorldLocation();
            });
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Returns the player's template-world coordinate.
     *
     * Obor's safe zone/arena is instanced. In that scene Actor#getWorldLocation()
     * can report instance-space coordinates (for example 130xx,929x), while the
     * user-supplied gate/Rocks Areas are template coordinates around 309x,98xx.
     *
     * WorldPoint#fromLocalInstance maps the player's LocalPoint back to the
     * original template coordinate and returns the normal world coordinate when
     * the current world view is not instanced.
     */
    private WorldPoint getPlayerTemplateWorldLocation() {
        try {
            return Microbot.getClientThread().invoke(() -> {
                if (Microbot.getClient().getLocalPlayer() == null) {
                    return null;
                }

                LocalPoint localPoint = Microbot.getClient().getLocalPlayer().getLocalLocation();
                if (localPoint == null) {
                    return null;
                }

                int plane = Microbot.getClient().getLocalPlayer().getWorldLocation().getPlane();
                return WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint, plane);
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private Actor getLocalPlayerActor() {
        try {
            return Microbot.getClientThread().invoke(
                    () -> Microbot.getClient().getLocalPlayer()
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isUnderground() {
        WorldPoint playerLocation = getPlayerWorldLocation();
        return playerLocation != null && playerLocation.getY() >= 9000;
    }

    private boolean isInsideOborGateArea(WorldPoint point) {
        return point != null && OBOR_GATE_AREA.contains(point);
    }

    private boolean isPlayerInsideOborGateArea() {
        return isInsideOborGateArea(getPlayerTemplateWorldLocation());
    }

    private boolean tryOpenOborGate() {
        WorldPoint playerLocation = getPlayerTemplateWorldLocation();

        /*
         * Only interact with Obor's Gate while inside the supplied area.
         * Outside it, walk into the area first. There is no exact-tile test.
         */
        if (!isInsideOborGateArea(playerLocation)) {
            Microbot.status = "Obor: walking into gate area";
            Rs2Walker.walkTo(OBOR_GATE_AREA_WALK_TARGET, 1);
            return false;
        }

        if (!canIssueImmediateInteraction()) {
            return false;
        }

        // Walking has finished. Explicitly clear its target so a stale walker
        // action cannot override/re-click while we are trying to open the gate.
        Rs2Walker.setTarget(null);
        Microbot.status = "Obor: opening lair gate";

        /*
         * Do NOT use Queryable#interact here.
         *
         * In Microbot 2.6.19 interact(action, distance) resolves the target via
         * nearestReachable(distance). A closed gate can be the collision object
         * that makes the tile on its far side unreachable, causing this exact
         * visible-at-the-gate stall.
         *
         * Select the nearest visible gate without the reachability filter, then
         * click its Open action directly.
         */
        Rs2TileObjectModel gate = Microbot.getRs2TileObjectCache().query()
                .withIds(OBOR_ENTRANCE_GATE_IDS)
                .nearest(12);

        if (gate == null) {
            /*
             * The supplied screenshots confirm this location is specifically
             * Obor's gate, so a name fallback is safe while tightly scoped to
             * the confirmed approach area.
             */
            gate = Microbot.getRs2TileObjectCache().query()
                    .withName("Gate")
                    .nearest(12);
        }

        if (gate == null) {
            Microbot.status = "Obor: gate not cached - retrying";
            return false;
        }

        Microbot.status = "Obor: clicking visible lair gate";
        boolean clickedGate = gate.click("Open");

        if (!clickedGate) {
            Microbot.status = "Obor: gate click failed - retrying";
            return false;
        }

        /*
         * The Gate/Door interaction itself moves the player into the next
         * safe-zone area. Do not issue any walking after this click.
         */
        oborGateTransitionPending = true;
        Microbot.status = "Obor: gate clicked - waiting for rocks area";
        return true;
    }

    private boolean isInsideOborRocksArea(WorldPoint point) {
        return point != null && OBOR_ROCKS_AREA.contains(point);
    }

    private boolean isPlayerInsideOborRocksArea() {
        return isInsideOborRocksArea(getPlayerTemplateWorldLocation());
    }

    private Rs2TileObjectModel findOborEntryRocks() {
        /*
         * Player area membership already proves we are standing at the correct
         * Obor Rocks transition.
         *
         * ID 29491 occurs multiple times in the instance, so:
         *   - never use first();
         *   - do not template-filter the objects themselves;
         *   - choose the physically closest 29491 to the player in the current
         *     WorldView using LocalPoint scene distance.
         *
         * This directly selects the pair beside the player (the Object Examiner
         * coordinates supplied by the user) rather than another 29491 elsewhere.
         */
        var candidates = Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withId(OBOR_ENTRY_ROCKS_ID)
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        LocalPoint playerLocal = getPlayerLocalLocation();
        if (playerLocal == null) {
            return null;
        }

        return candidates.stream()
                .filter(object -> object.getLocalLocation() != null)
                .min(Comparator.comparingInt(object ->
                        localDistance(playerLocal, object.getLocalLocation())))
                .orElse(null);
    }

    private LocalPoint getPlayerLocalLocation() {
        try {
            return Microbot.getClientThread().invoke(() -> {
                if (Microbot.getClient().getLocalPlayer() == null) {
                    return null;
                }
                return Microbot.getClient().getLocalPlayer().getLocalLocation();
            });
        } catch (Exception ex) {
            return null;
        }
    }


    private int localDistance(LocalPoint a, LocalPoint b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }

        int dx = Math.abs(a.getSceneX() - b.getSceneX());
        int dy = Math.abs(a.getSceneY() - b.getSceneY());
        return Math.max(dx, dy);
    }

    private void handleBossEntry() {
        /*
         * Do NOT use isInsideBossLair() as the generic first check here.
         *
         * Obor's entry safe-zone contains the lair Chest + Rocks, so the old
         * predicate returned true immediately after opening the gate. That
         * incorrectly promoted ENTERING_BOSS -> FIGHTING_BOSS before the
         * required Rocks -> Climb-down interaction happened.
         */
        if (activeTarget == BossTarget.OBOR) {
            /*
             * Strict Obor entry sequence:
             *
             * 1. Be inside OBOR_GATE_AREA.
             * 2. Click Gate/Door -> Open.
             * 3. Wait ONLY for the player location to enter OBOR_ROCKS_AREA.
             * 4. Once inside OBOR_ROCKS_AREA, click nearest Rocks -> Climb-down.
             * 5. Wait ONLY for the player to leave OBOR_ROCKS_AREA.
             * 6. Then permit fighting Obor.
             *
             * There are no fixed delays and no walking between steps 2 and 3.
             */
            if (oborArenaEntered) {
                Microbot.status = "Obor: arena entered - waiting for boss";

                if (findBoss(BossTarget.OBOR) != null) {
                    bossSeenThisFight = false;
                    state = FarmerState.FIGHTING_BOSS;
                }
                return;
            }

            WorldPoint playerLocation = getPlayerTemplateWorldLocation();
            if (playerLocation == null) {
                return;
            }

            /*
             * After Rocks were clicked, the ONLY completion condition is that
             * the player has left the supplied Rocks area.
             */
            if (oborRocksTransitionPending) {
                Rs2Walker.setTarget(null);

                if (!isInsideOborRocksArea(playerLocation)) {
                    oborRocksTransitionPending = false;
                    oborRocksAreaEnteredAt = 0L;
                    oborArenaEntered = true;
                    Microbot.status = "Obor: rocks transition complete";

                    if (findBoss(BossTarget.OBOR) != null) {
                        state = FarmerState.FIGHTING_BOSS;
                    }
                } else {
                    Microbot.status = "Obor: waiting for rocks transition";
                }
                return;
            }

            /*
             * Once the Gate/Door was clicked, do not path or click the gate
             * again. Wait until RuneScape places the player in the next area.
             */
            if (oborGateTransitionPending) {
                Rs2Walker.setTarget(null);

                if (!isInsideOborRocksArea(playerLocation)) {
                    Microbot.status = "Obor: waiting to enter rocks area";
                    return;
                }

                oborGateTransitionPending = false;
                oborRocksAreaEnteredAt = System.currentTimeMillis();
                Microbot.status = "Obor: rocks area entered - settling";
                return;
            }

            /*
             * Inside the supplied Rocks area: the Rocks interaction becomes the
             * absolute priority. No gate logic and no pathing is allowed here.
             */
            if (isInsideOborRocksArea(playerLocation)) {
                Rs2Walker.setTarget(null);

                /*
                 * Entering the safe-zone area can report the new position before
                 * the transition/scene is fully settled. Give it exactly one
                 * game-tick window before issuing Climb-down.
                 */
                if (oborRocksAreaEnteredAt == 0L) {
                    oborRocksAreaEnteredAt = System.currentTimeMillis();
                    Microbot.status = "Obor: rocks area entered - settling";
                    return;
                }

                long rocksAreaElapsed = System.currentTimeMillis() - oborRocksAreaEnteredAt;
                if (rocksAreaElapsed < OBOR_ROCKS_SETTLE_MS) {
                    Microbot.status = "Obor: waiting one tick before Climb-down";
                    return;
                }

                /*
                 * Once the settle window has passed, only actual player movement
                 * blocks the interaction.
                 */
                if (Rs2Player.isMoving()) {
                    return;
                }

                Rs2TileObjectModel entryRocks = findOborEntryRocks();

                if (entryRocks == null) {
                    Microbot.status = "Obor: rocks area - finding nearest 29491";
                    return;
                }

                Microbot.status = "Obor: nearest Rocks 29491 - Climb-down";
                boolean climbed = entryRocks.click("Climb-down");

                if (climbed) {
                    bossSeenThisFight = false;
                    oborRocksTransitionPending = true;
                    oborRocksAreaEnteredAt = 0L;
                    Microbot.status = "Obor: Rocks clicked - waiting for arena";
                }
                return;
            }

            /*
             * Before Gate/Door interaction, move only into the supplied gate
             * area. Once there, tryOpenOborGate() performs Open and establishes
             * oborGateTransitionPending.
             */
            if (!isInsideOborGateArea(playerLocation)) {
                Microbot.status = "Obor: walking into gate area";
                Rs2Walker.walkTo(OBOR_GATE_AREA_WALK_TARGET, 1);
                return;
            }

            tryOpenOborGate();
            return;
        }

        // Bryophyta does not use Obor's safe-zone rocks transition.
        if (isInsideBossLair()) {
            state = FarmerState.FIGHTING_BOSS;
            return;
        }

        if (!canIssueImmediateInteraction()) {
            return;
        }

        if (config.useProtectionPrayers()) {
            ensureProtectionPrayer(BossTarget.BRYOPHYTA);
        }

        boolean clickedGate = Microbot.getRs2TileObjectCache().query()
                .withName("Gate")
                .interact("Open", 25);

        if (clickedGate) {
            bossSeenThisFight = false;
            Microbot.status = "Bryophyta: gate clicked";
        }
    }

    private void handleBossFight() {
        /*
         * No food means the fight is over immediately from the script's point
         * of view. Do not attempt another attack, Growthling action, weapon
         * swap, chest state, etc. Escape first, then recover at bank/altar.
         */
        if (Rs2Inventory.getInventoryFood().isEmpty()) {
            emergencyRecoveryRequested = true;
            Microbot.status = "Out of food - escaping boss";
            state = FarmerState.EXITING_BOSS;
            return;
        }

        /*
         * Defense-in-depth: stale state recovery must never make the client
         * attack Obor through the gate. The fight is legal only after the
         * Climb-down rocks transition has been confirmed.
         */
        if (activeTarget == BossTarget.OBOR && !oborArenaEntered) {
            Microbot.status = "Obor: fight blocked until rocks are climbed";
            state = FarmerState.ENTERING_BOSS;
            return;
        }

        eatIfNeeded();

        if (config.useProtectionPrayers()) {
            ensureProtectionPrayer(activeTarget);
        }

        if (activeTarget == BossTarget.BRYOPHYTA && handleGrowthlings()) {
            return;
        }

        restoreCombatWeaponIfNeeded();

        Rs2NpcModel boss = findBoss(activeTarget);

        if (boss != null && !boss.isDead()) {
            bossSeenThisFight = true;

            if (getPlayerWorldLocation() != null
                    && !Rs2Player.isInteracting()
                    && !Rs2Player.isAnimating()) {
                boss.click("Attack");
            }
            return;
        }

        if (!bossSeenThisFight) {
            return;
        }

        if (config.lootBossKeyDrops() && tryLootMatchingKey(activeTarget)) {
            return;
        }

        state = FarmerState.OPENING_CHEST;
    }

    private void handleChest() {
        disableProtectionPrayer();

        if (config.lootBossKeyDrops() && tryLootMatchingKey(activeTarget)) {
            return;
        }

        int currentKeys = Rs2Inventory.itemQuantity(activeTarget.getKeyName());

        if (pendingChestKeyCount >= 0) {
            if (currentKeys < pendingChestKeyCount) {
                if (activeTarget == BossTarget.OBOR) {
                    oborChests++;
                } else {
                    bryophytaChests++;
                }

                pendingChestKeyCount = -1;
                lastCompletedBoss = activeTarget;
                bossSeenThisFight = false;
                state = FarmerState.EXITING_BOSS;
                return;
            }

            // The click is still being processed. Do not impose a timer; retry
            // only as soon as the player is idle and the key has not changed.
            if (!canIssueImmediateInteraction()) {
                return;
            }
        }

        if (currentKeys <= 0) {
            pendingChestKeyCount = -1;
            lastCompletedBoss = activeTarget;
            state = FarmerState.EXITING_BOSS;
            return;
        }

        if (!canIssueImmediateInteraction()) {
            return;
        }

        boolean clicked;
        if (activeTarget == BossTarget.BRYOPHYTA) {
            Rs2TileObjectModel chest = Microbot.getRs2TileObjectCache().query()
                    .withId(BRYOPHYTA_CHEST_ID)
                    .nearest(20);
            clicked = chest != null && chest.click("Open");
        } else {
            Rs2TileObjectModel chest = Microbot.getRs2TileObjectCache().query()
                    .withName("Chest")
                    .nearest(20);
            clicked = chest != null && chest.click("Open");
        }

        if (clicked) {
            pendingChestKeyCount = currentKeys;
            Microbot.status = "Chest clicked - checking key consumption";
        }
    }

    private void handleBossExit() {
        /*
         * During an out-of-food emergency keep the current protection prayer
         * active while physically escaping. Disable it once safely outside.
         */
        if (!emergencyRecoveryRequested) {
            disableProtectionPrayer();
        }

        if (!isInsideBossLair()) {
            disableProtectionPrayer();

            if (emergencyRecoveryRequested) {
                Microbot.status = "Boss escaped - recovering at bank";
                bankInventoryCleared = false;
                bankLoadoutPrepared = false;
                state = FarmerState.BANKING;
                return;
            }

            choosePostBossState();
            return;
        }

        if (!canIssueImmediateInteraction()) {
            return;
        }

        boolean exited = false;

        if (activeTarget == BossTarget.BRYOPHYTA) {
            exited = Microbot.getRs2TileObjectCache().query()
                    .withId(BRYOPHYTA_EXIT_ROCK_PILE_ID)
                    .interact("Quick-exit");

            if (!exited) {
                exited = Microbot.getRs2TileObjectCache().query()
                        .withId(BRYOPHYTA_EXIT_ROCK_PILE_ID)
                        .interact("Clamber");
            }
        } else {
            exited = Microbot.getRs2TileObjectCache().query()
                    .withName("Rocks")
                    .interact("Climb-up");

            if (!exited) {
                exited = Microbot.getRs2TileObjectCache().query()
                        .withName("Gate")
                        .interact("Quick-exit");
            }

            if (!exited) {
                exited = Microbot.getRs2TileObjectCache().query()
                        .withName("Gate")
                        .interact("Open");
            }
        }

        if (exited) {
            Microbot.status = "Boss exit clicked";
            return;
        }

        if (!isInsideBossLair()) {
            disableProtectionPrayer();

            if (emergencyRecoveryRequested) {
                Microbot.status = "Boss escaped - recovering at bank";
                bankInventoryCleared = false;
                bankLoadoutPrepared = false;
                state = FarmerState.BANKING;
            } else {
                choosePostBossState();
            }
        }
    }

    private void choosePostBossState() {
        if (activeTarget == BossTarget.OBOR) {
            oborArenaEntered = false;
            oborGateTransitionPending = false;
            oborRocksTransitionPending = false;
            oborRocksAreaEnteredAt = 0L;
        }

        int remainingMatchingKeys = Rs2Inventory.itemQuantity(activeTarget.getKeyName());

        // Re-fight immediately when another matching key is already carried and
        // the current supplies/equipment are still valid.
        if (remainingMatchingKeys > 0 && hasRequiredLoadout(activeTarget)) {
            routeCurrentLoadout(activeTarget);
            return;
        }

        BossTarget nextTarget = activeTarget;

        if (config.mode() == BossMode.AUTO && config.alternateAfterChest()) {
            lastCompletedBoss = activeTarget;
            nextTarget = activeTarget.opposite();
        }

        /*
         * Do not bank merely because a boss cycle ended. If the inventory and
         * equipment already satisfy the next target, continue directly. This
         * permits key farming without a pointless deposit/withdraw cycle.
         */
        if (hasRequiredLoadout(nextTarget)) {
            routeCurrentLoadout(nextTarget);
            return;
        }

        activeTarget = nextTarget;
        requestBanking("Next loadout requires bank");
    }

    private boolean handleGrowthlings() {
        Rs2NpcModel growthling = Microbot.getRs2NpcCache().query()
                .withName("Growthling")
                .where(n -> !n.isDead())
                .nearestOnClientThread();

        if (growthling == null) {
            return false;
        }

        String axe = clean(config.growthlingAxe());

        if (!axe.isEmpty() && !Rs2Equipment.isWearing(axe)) {
            if (Rs2Inventory.hasItem(axe)) {
                Rs2Inventory.interact(axe, "Wield");
                return true;
            }

            Microbot.showMessage("Growthlings active but axe is unavailable: " + axe);
            return true;
        }

        if (getPlayerWorldLocation() != null
                && !Rs2Player.isInteracting()) {
            growthling.click("Attack");
        }

        return true;
    }

    private void restoreCombatWeaponIfNeeded() {
        equipCombatWeaponIfAvailable();
    }

    private void equipCombatWeaponIfAvailable() {
        String weapon = clean(config.combatWeapon());
        if (weapon.isEmpty() || Rs2Equipment.isWearing(weapon)) {
            return;
        }

        if (Rs2Inventory.hasItem(weapon)) {
            Rs2Inventory.interact(weapon, "Wield");
        }
    }

    private Rs2NpcModel findGiant(BossTarget target) {
        Actor localPlayer = getLocalPlayerActor();
        if (localPlayer == null) {
            return null;
        }

        return Microbot.getRs2NpcCache().query()
                .where(n -> n.getName() != null
                        && n.getName().equalsIgnoreCase(target.getGiantName()))
                .where(n -> !n.isDead())
                .where(n -> n.getInteracting() == null
                        || Objects.equals(n.getInteracting(), localPlayer))
                .nearestOnClientThread();
    }

    private Rs2NpcModel findBoss(BossTarget target) {
        return Microbot.getRs2NpcCache().query()
                .where(n -> n.getName() != null
                        && n.getName().equalsIgnoreCase(target.getBossName()))
                .where(n -> !n.isDead())
                .nearestOnClientThread();
    }

    private boolean tryLootMatchingKey(BossTarget target) {
        Rs2TileItemModel key = Microbot.getRs2TileItemCache().query()
                .withName(target.getKeyName())
                .where(Rs2TileItemModel::isLootAble)
                .nearest(18);

        if (key == null) {
            return false;
        }

        // Do not spam while the previous pickup is physically moving the
        // player. Retry immediately on the first idle cycle if still present.
        if (!canIssueImmediateInteraction()) {
            return true;
        }

        return key.pickup();
    }

    private boolean isInsideBossLair() {
        if (findBoss(BossTarget.OBOR) != null || findBoss(BossTarget.BRYOPHYTA) != null) {
            return true;
        }

        if (Microbot.getRs2TileObjectCache().query()
                .withId(BRYOPHYTA_CHEST_ID)
                .nearest(30) != null) {
            return true;
        }

        return Microbot.getRs2TileObjectCache().query()
                .withName("Chest")
                .nearest(25) != null
                && Microbot.getRs2TileObjectCache().query()
                .withName("Rocks")
                .nearest(25) != null;
    }

    private void eatIfNeeded() {
        if (getPlayerWorldLocation() == null) {
            return;
        }

        if (Rs2Inventory.getInventoryFood().isEmpty()) {
            return;
        }

        Rs2Player.eatAt(config.eatAtPercent());
    }

    private int getBoostedLevel(Skill skill) {
        try {
            Integer value = Microbot.getClientThread().invoke(
                    () -> Microbot.getClient().getBoostedSkillLevel(skill)
            );
            return value == null ? 0 : value;
        } catch (Exception ex) {
            return 0;
        }
    }

    private int getRealLevel(Skill skill) {
        try {
            Integer value = Microbot.getClientThread().invoke(
                    () -> Microbot.getClient().getRealSkillLevel(skill)
            );
            return value == null ? 0 : value;
        } catch (Exception ex) {
            return 0;
        }
    }

    private boolean isFullHealth() {
        int current = getBoostedLevel(Skill.HITPOINTS);
        int maximum = getRealLevel(Skill.HITPOINTS);
        return maximum > 0 && current >= maximum;
    }

    private boolean needsPrayerRestore() {
        int current = getBoostedLevel(Skill.PRAYER);
        int maximum = getRealLevel(Skill.PRAYER);
        return maximum > 0 && current < maximum;
    }

    private boolean isInsidePrayerAltarArea(WorldPoint point) {
        return point != null && PRAYER_ALTAR_AREA.contains(point);
    }

    private void handlePrayerRestore() {
        disableProtectionPrayer();

        if (!needsPrayerRestore()) {
            Microbot.status = "Prayer restored - resuming";
            emergencyRecoveryRequested = false;
            routeCurrentLoadout(activeTarget);
            return;
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }

        WorldPoint playerLocation = getPlayerWorldLocation();
        if (playerLocation == null) {
            return;
        }

        /*
         * Do not attempt random Altars on the way. Walk into the exact
         * user-confirmed altar area first.
         */
        if (!isInsidePrayerAltarArea(playerLocation)) {
            Microbot.status = "Walking to Prayer altar area";
            Rs2Walker.walkTo(PRAYER_ALTAR_AREA_WALK_TARGET, 4);
            return;
        }

        /*
         * We are in the confirmed altar area. Stop pathing and immediately use
         * the nearest actual Altar scenery object.
         */
        Rs2Walker.setTarget(null);

        if (Rs2Player.isMoving()) {
            return;
        }

        Rs2TileObjectModel altar = Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withName("Altar")
                .nearest(12);

        if (altar == null) {
            Microbot.status = "Inside Prayer altar area - finding Altar";
            return;
        }

        Microbot.status = "Restoring Prayer - Pray-at Altar";
        altar.click("Pray-at");
    }

    private void ensureProtectionPrayer(BossTarget target) {
        if (!config.useProtectionPrayers()) {
            return;
        }

        int prayer = getBoostedLevel(Skill.PRAYER);
        if (prayer <= 0) {
            return;
        }

        Rs2PrayerEnum desired = target == BossTarget.OBOR
                ? Rs2PrayerEnum.PROTECT_RANGE
                : Rs2PrayerEnum.PROTECT_MAGIC;

        if (!Rs2Prayer.isPrayerActive(desired)) {
            Rs2Prayer.toggle(desired, true);
        }
    }

    private void disableProtectionPrayer() {
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
        }

        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
        }
    }

    private void requestBanking(String status) {
        if (status != null && !status.isEmpty()) {
            Microbot.status = status;
        }

        bankInventoryCleared = false;
        bankLoadoutPrepared = false;
        state = FarmerState.BANKING;
    }

    private boolean needsBank() {
        return !hasRequiredLoadout(activeTarget);
    }

    private WorldPoint giantAnchor(BossTarget target) {
        if (target == BossTarget.OBOR) {
            return new WorldPoint(config.hillAnchorX(), config.hillAnchorY(), 0);
        }

        return new WorldPoint(config.mossAnchorX(), config.mossAnchorY(), 0);
    }

    private int bankQuantity(String name) {
        return Rs2Bank.count(name, true);
    }

    /**
     * No artificial interaction cooldown. An action becomes eligible on the
     * first cycle where the player is no longer moving/animating.
     */
    private boolean canIssueImmediateInteraction() {
        return !Rs2Player.isMoving() && !Rs2Player.isAnimating();
    }

    private void updateSessionKeyCounters() {
        sessionGiantKeys = Rs2Inventory.itemQuantity(BossTarget.OBOR.getKeyName());
        sessionMossyKeys = Rs2Inventory.itemQuantity(BossTarget.BRYOPHYTA.getKeyName());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public FarmerState getState() {
        return state;
    }

    public BossTarget getActiveTarget() {
        return activeTarget;
    }

    public int getSessionGiantKeys() {
        return sessionGiantKeys;
    }

    public int getSessionMossyKeys() {
        return sessionMossyKeys;
    }

    public int getOborChests() {
        return oborChests;
    }

    public int getBryophytaChests() {
        return bryophytaChests;
    }

    @Override
    public void shutdown() {
        disableProtectionPrayer();
        Rs2Walker.setTarget(null);
        state = FarmerState.STARTING;
        super.shutdown();
    }
}
