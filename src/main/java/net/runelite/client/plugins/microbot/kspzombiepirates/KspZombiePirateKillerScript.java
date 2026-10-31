package net.runelite.client.plugins.microbot.kspzombiepirates;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspZombiePirateKillerScript extends Script {
    private static final WorldArea FEROX_AREA = inclusiveArea(3130, 3636, 3140, 3620, 0);
    private static final WorldPoint FEROX_CENTER = new WorldPoint(3135, 3628, 0);

    private static final int ZOMBIE_PIRATE_MIN_X = 3218;
    private static final int ZOMBIE_PIRATE_MAX_X = 3263;
    private static final int ZOMBIE_PIRATE_MIN_Y = 3590;
    private static final int ZOMBIE_PIRATE_MAX_Y = 3637;
    private static final int ZOMBIE_PIRATE_PLANE = 0;
    private static final WorldArea ZOMBIE_PIRATE_AREA = inclusiveArea(
            ZOMBIE_PIRATE_MIN_X, ZOMBIE_PIRATE_MAX_Y, ZOMBIE_PIRATE_MAX_X, ZOMBIE_PIRATE_MIN_Y, ZOMBIE_PIRATE_PLANE);
    private static final WorldPoint ZOMBIE_PIRATE_CENTER = new WorldPoint(3236, 3620, 0);

    // Do not activate prayer during the Ferox/long-walk approach. Walk here first,
    // then enable the configured prayers only inside this northern entry rectangle.
    // Equivalent target area: new Area(3230, 3636, 3241, 3633).
    private static final WorldArea ZOMBIE_PIRATE_PRAYER_ACTIVATION_AREA = inclusiveArea(
            3230, 3636, 3241, 3633, ZOMBIE_PIRATE_PLANE);
    private static final WorldPoint ZOMBIE_PIRATE_PRAYER_ACTIVATION_POINT = new WorldPoint(3235, 3634, ZOMBIE_PIRATE_PLANE);

    private static final int PRAYER_ALTAR_ID = 411;
    private static final WorldPoint PRAYER_ALTAR_POINT = new WorldPoint(3239, 3608, 0);
    private static final WorldPoint PRAYER_ALTAR_STAND_POINT = new WorldPoint(3239, 3607, 0);

    private static final String TARGET_NPC = "Zombie pirate";
    private static final String LOOTING_BAG = "Looting bag";
    private static final String BLIGHTED_KARAMBWAN = "Blighted karambwan";
    private static final String EAT_ACTION = "Eat";

    private static final int LOOP_DELAY_MS = 150;
    private static final int LOOT_RETRY_DELAY_MS = 100;
    private static final int ATTACK_RETRY_DELAY_MS = 150;
    private static final int ATTACK_CONFIRM_TIMEOUT_MS = 250;
    private static final int ALTAR_RETRY_DELAY_MS = 180;
    private static final int ALTAR_CONFIRM_TIMEOUT_MS = 350;


    private static final String DEFAULT_EQUIPMENT_NAMES = String.join("\n",
            "Salve amulet",
            "Ardougne cloak",
            "Dragon scimitar",
            "Monk's robe top",
            "Anti-dragon shield",
            "Monk's robe",
            "Climbing boots",
            "Barrows gloves",
            "Berserker ring");

    @Getter
    private volatile KspZombiePirateKillerState state = KspZombiePirateKillerState.STOPPED;

    @Getter
    private volatile String status = "Idle";

    @Getter
    private volatile int trips;

    private volatile boolean hardStopped;
    private volatile boolean lootingBagFull;
    private long lastLootingBagOpenAttempt;
    private long lastAttackAttempt;
    private long lastLootAttempt;
    private volatile boolean wasInZombiePirateArea;
    private long lastTeleportAttempt;
    private long lastPoolAttempt;
    private long lastBankAttempt;
    private long lastAltarAttempt;
    private long lastEatAttempt;
    private int lastClickedNpcIndex = -1;
    private long lastClickedNpcAt;

    public boolean run(KspZombiePirateKillerConfig config) {
        state = KspZombiePirateKillerState.STARTING;
        status = "Starting";
        hardStopped = false;
        lootingBagFull = false;
        wasInZombiePirateArea = false;
        lastClickedNpcIndex = -1;
        lastClickedNpcAt = 0L;
        lastEatAttempt = 0L;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (!super.run()) {
                    return;
                }

                loop(config);
            } catch (Exception ex) {
                log.error("[KSP Zombie Pirates] Script loop failed", ex);
                Microbot.status = "KSP Zombie Pirates error: " + ex.getMessage();
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        state = KspZombiePirateKillerState.STOPPED;
        if (!hardStopped) {
            status = "Stopped";
        }
        super.shutdown();
    }

    public void markLootingBagFull() {
        lootingBagFull = true;
        status = "Looting bag full";
    }

    private void loop(KspZombiePirateKillerConfig config) {
        if (hardStopped) {
            return;
        }

        boolean inZombiePirateArea = isInZombiePirateArea();

        // Food is a safety action and must be allowed to interrupt combat, looting,
        // prayer restoration, and local movement. Do this before altar/prayer/travel
        // routing so a low-HP player does not idle or run to the altar instead of eating.
        if (!Rs2Bank.isOpen() && eatFoodIfNeeded(config)) {
            return;
        }

        if (shouldRestorePrayerAtChaosAltar(config, inZombiePirateArea)) {
            prayAtNearestAltar(config);
            return;
        }

        handleTravelAndAreaPrayer(config, inZombiePirateArea);

        if (shouldReturnToFerox(config)) {
            walkToFerox(config);
            return;
        }

        if (isInFerox()) {
            handleFerox(config);
            return;
        }

        if (!inZombiePirateArea) {
            walkToZombiePirates(config);
            return;
        }

        if (!configuredPrayersAlreadyActive(config)) {
            if (!isAtZombiePiratePrayerActivationPoint(Rs2Player.getWorldLocation())) {
                status = "Walking to prayer activation area";
                Rs2Walker.walkTo(ZOMBIE_PIRATE_PRAYER_ACTIVATION_POINT);
                return;
            }

            if (!ensureConfiguredPrayersActive(config)) {
                return;
            }
        }

        handleZombiePirates(config);
    }

    private void handleFerox(KspZombiePirateKillerConfig config) {
        if (needsRefresh()) {
            drinkFromPool();
            return;
        }

        if (!hasRunnableInventory(config) || !isConfiguredGearEquipped(config) || lootingBagFull || Rs2Inventory.isFull()) {
            bankAndRestock(config);
            return;
        }

        walkToZombiePirates(config);
    }

    private void handleTravelAndAreaPrayer(KspZombiePirateKillerConfig config, boolean inZombiePirateArea) {
        if (!config.keepProtectMagic() && configuredActivePrayers(config).isEmpty()) {
            wasInZombiePirateArea = inZombiePirateArea;
            return;
        }

        if (inZombiePirateArea && isReadyToEnterZombiePirates(config)
                && isAtZombiePiratePrayerActivationPoint(Rs2Player.getWorldLocation())) {
            state = KspZombiePirateKillerState.FIGHTING;
            status = wasInZombiePirateArea ? status : "Activating Protect from Magic";
            ensureConfiguredPrayersActive(config);
            wasInZombiePirateArea = true;
            return;
        }

        wasInZombiePirateArea = false;

        if (shouldPreActivatePrayersWhileWalkingToPirates(config)) {
            ensureConfiguredPrayersActive(config);
        }
    }

    private boolean shouldPreActivatePrayersWhileWalkingToPirates(KspZombiePirateKillerConfig config) {
        if (!isReadyToEnterZombiePirates(config)) {
            return false;
        }

        if (Rs2Prayer.isOutOfPrayer()) {
            return false;
        }

        if (!config.keepProtectMagic() && configuredActivePrayers(config).isEmpty()) {
            return false;
        }

        if (configuredPrayersAlreadyActive(config)) {
            return false;
        }

        return isAtZombiePiratePrayerActivationPoint(Rs2Player.getWorldLocation());
    }

    private boolean isAtZombiePiratePrayerActivationPoint(WorldPoint playerPoint) {
        if (playerPoint == null || playerPoint.getPlane() != ZOMBIE_PIRATE_PLANE) {
            return false;
        }

        return ZOMBIE_PIRATE_PRAYER_ACTIVATION_AREA.contains(playerPoint);
    }

    private boolean isReadyToEnterZombiePirates(KspZombiePirateKillerConfig config) {
        if (config == null || Rs2Bank.isOpen()) {
            return false;
        }

        if (lootingBagFull || Rs2Inventory.isFull()) {
            return false;
        }

        if (isInFerox() && needsRefresh()) {
            return false;
        }

        return isConfiguredGearEquipped(config) && hasRunnableInventory(config);
    }

    private boolean isConfiguredGearEquipped(KspZombiePirateKillerConfig config) {
        for (String equipmentName : configuredEquipmentNames(config)) {
            if (equipmentName == null || equipmentName.isBlank()) {
                continue;
            }

            if (!isWearingConfiguredEquipment(equipmentName)) {
                return false;
            }
        }

        return true;
    }

    private boolean configuredPrayersAlreadyActive(KspZombiePirateKillerConfig config) {
        if (config.keepProtectMagic() && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
            return false;
        }

        for (Rs2PrayerEnum prayer : configuredActivePrayers(config)) {
            if (prayer != null && !Rs2Prayer.isPrayerActive(prayer)) {
                return false;
            }
        }

        return true;
    }

    private boolean shouldRestorePrayerAtChaosAltar(KspZombiePirateKillerConfig config, boolean inZombiePirateArea) {
        if (!config.prayAtNearestAltar() || !inZombiePirateArea) {
            return false;
        }

        int currentPrayer = Rs2Prayer.getPrayerPoints();
        int realPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        return currentPrayer <= config.drinkPrayerAt() && currentPrayer < realPrayer;
    }

    private void handleZombiePirates(KspZombiePirateKillerConfig config) {
        ensureConfiguredPrayersActive(config);

        if (eatFoodIfNeeded(config)) {
            return;
        }

        if (Rs2Prayer.getPrayerPoints() <= config.drinkPrayerAt()) {
            if (config.prayAtNearestAltar()) {
                prayAtNearestAltar(config);
                return;
            }

            if (drinkPrayerRestore()) {
                sleep(220, 380);
                return;
            }
        }

        ensureLootingBagOpen();

        if (lootGroundItems(config)) {
            state = KspZombiePirateKillerState.LOOTING;
            status = "Looting";
            resumeCombatAfterLooting(config);
            return;
        }

        if (isStillFightingLastClickedNpc()) {
            state = KspZombiePirateKillerState.FIGHTING;
            status = "Fighting Zombie pirate";
            return;
        }

        // If the NPC we last attacked despawned/died after looting, immediately move on.
        // This avoids getting stuck on stale combat/interacting state after the kill.
        clearLastClickedNpcIfGone();
        attackZombiePirate(config);
    }

    private boolean shouldReturnToFerox(KspZombiePirateKillerConfig config) {
        if (isInFerox()) {
            return false;
        }

        if (lootingBagFull || Rs2Inventory.isFull()) {
            return true;
        }

        if (!isConfiguredGearEquipped(config)) {
            return true;
        }

        // Do not return just because the configured karambwan quantity dropped below
        // the original restock amount. Only bank once there are no Blighted karambwans left.
        if (isBlightedKarambwanConfigured(config) && !hasBlightedKarambwan()) {
            return true;
        }

        // During a trip, keep fighting until an actual safety condition is hit.
        if (currentHitpointsPercent() <= config.eatAtHp() && !hasFoodToEat(config)) {
            return true;
        }

        if (currentHitpointsPercent() <= config.emergencyTeleportHp()) {
            return true;
        }

        if (!config.prayAtNearestAltar() && Rs2Prayer.getPrayerPoints() <= config.bankWhenPrayerBelow() && !hasPrayerRestore()) {
            return true;
        }

        return false;
    }

    private void walkToFerox(KspZombiePirateKillerConfig config) {
        state = KspZombiePirateKillerState.RETURNING_TO_FEROX;
        status = "Walking to Ferox";

        eatFoodIfNeeded(config);

        final long now = System.currentTimeMillis();
        if (now - lastTeleportAttempt < 1_200) {
            return;
        }
        lastTeleportAttempt = now;

        Rs2Walker.walkTo(FEROX_CENTER);
        sleep(300, 650);

        if (isInFerox()) {
            trips++;
            lootingBagFull = false;
        }
    }

    private boolean needsRefresh() {
        if (currentHitpointsPercent() < 95) {
            return true;
        }

        int realPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        return Rs2Prayer.getPrayerPoints() < realPrayer;
    }

    private void drinkFromPool() {
        state = KspZombiePirateKillerState.HEALING;
        status = "Refreshing at pool";

        final long now = System.currentTimeMillis();
        if (now - lastPoolAttempt < 4_000) {
            return;
        }
        lastPoolAttempt = now;

        boolean clicked = Rs2GameObject.interact("Pool of Refreshment", "Drink", false)
                || Rs2GameObject.interact("Pool of Refreshment", "Drink-from", false)
                || Rs2GameObject.interact("Pool of Refreshment", "Drink", true)
                || Rs2GameObject.interact("Pool of Refreshment", "Drink-from", true);

        if (clicked) {
            sleepUntil(() -> !needsRefresh(), 10_000);
        }
    }

    private void bankAndRestock(KspZombiePirateKillerConfig config) {
        state = KspZombiePirateKillerState.BANKING;
        status = "Banking";

        final long now = System.currentTimeMillis();
        if (now - lastBankAttempt < 1_200) {
            return;
        }
        lastBankAttempt = now;

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.walkToBank();
            sleep(300, 600);
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5_000);
            return;
        }

        if (config.useEmptyContainersButton()) {
            clickEmptyContainers(config.emptyContainerWidgetId());
            sleep(900, 1_500);
        }

        if (!Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 5_000);
        }

        state = KspZombiePirateKillerState.RESTOCKING;
        status = "Restocking";

        if (!validateLoadoutAvailable(config)) {
            return;
        }

        List<String> equipmentNames = configuredEquipmentNames(config);
        if (!withdrawConfiguredGearSet(equipmentNames)) {
            return;
        }

        if (!equipConfiguredGearSet(equipmentNames)) {
            return;
        }

        if (!restockInventoryItems(config)) {
            return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
        lootingBagFull = false;
        ensureLootingBagOpen();
    }

    private boolean clickEmptyContainers(int widgetId) {
        // Do not call Widget#isHidden() from the script thread.
        // Rs2Widget.clickWidget(int) performs the widget lookup/visibility check through the client thread.
        if (widgetId > 0 && Rs2Widget.clickWidget(widgetId)) {
            return true;
        }

        // Fallback to Microbot's helper if the configured widget is absent.
        return Rs2Bank.emptyContainers();
    }

    private boolean hasRunnableInventory(KspZombiePirateKillerConfig config) {
        for (WithdrawItem item : configuredInventoryItems(config)) {
            if (item.amount <= 0) {
                continue;
            }

            // Food is consumed during the trip. Do not force a bank/restock just because
            // the configured karambwan quantity is no longer full; only require that at
            // least one Blighted karambwan remains.
            if (isBlightedKarambwanName(item.name)) {
                if (!hasBlightedKarambwan()) {
                    return false;
                }
                continue;
            }

            if (!hasInventoryAmount(item.name, item.amount)) {
                return false;
            }
        }

        return true;
    }

    private boolean restockInventoryItems(KspZombiePirateKillerConfig config) {
        for (WithdrawItem item : configuredInventoryItems(config)) {
            if (item.amount <= 0) {
                continue;
            }

            int currentAmount = inventoryAmount(item.name);
            int missingAmount = item.amount - currentAmount;
            if (missingAmount <= 0) {
                continue;
            }

            if (!Rs2Bank.hasBankItem(item.name, missingAmount, false)) {
                stopWithReason("Missing inventory item: " + item.name + " x" + missingAmount
                        + " required for restock. Required=" + item.amount + ", inventory=" + currentAmount + ".");
                return false;
            }

            if (!Rs2Bank.withdrawX(item.name, missingAmount)) {
                stopWithReason("Failed to withdraw inventory item: " + item.name + " x" + missingAmount + ".");
                return false;
            }

            if (!sleepUntil(() -> hasInventoryAmount(item.name, item.amount), 2_500)) {
                stopWithReason("Restock failed for " + item.name + ". Required=" + item.amount
                        + ", inventory=" + inventoryAmount(item.name) + ".");
                return false;
            }
            sleep(450, 800);
        }

        return true;
    }

    private boolean validateLoadoutAvailable(KspZombiePirateKillerConfig config) {
        if (!Rs2Bank.isOpen()) {
            return true;
        }

        for (String equipmentName : configuredEquipmentNames(config)) {
            boolean exact = requiresExactEquipmentMatch(equipmentName);
            if (isWearingConfiguredEquipment(equipmentName) || hasInventoryEquipmentItem(equipmentName)) {
                continue;
            }

            if (!Rs2Bank.hasBankItem(equipmentName, 1, exact)) {
                stopWithReason("Missing gear: " + equipmentName + " was not equipped, not in inventory, and not found in the bank.");
                return false;
            }
        }

        for (WithdrawItem item : configuredInventoryItems(config)) {
            if (item.amount <= 0) {
                continue;
            }

            int currentAmount = inventoryAmount(item.name);
            int missingAmount = item.amount - currentAmount;
            if (missingAmount <= 0) {
                continue;
            }

            if (!Rs2Bank.hasBankItem(item.name, missingAmount, false)) {
                stopWithReason("Missing inventory item: " + item.name + " x" + missingAmount
                        + " required for restock. Required=" + item.amount + ", inventory=" + currentAmount + ".");
                return false;
            }
        }

        return true;
    }

    private List<String> configuredEquipmentNames(KspZombiePirateKillerConfig config) {
        List<String> customGear = parseNames(config.equipmentNames());
        if (!customGear.isEmpty()) {
            return customGear;
        }

        return parseNames(DEFAULT_EQUIPMENT_NAMES);
    }

    private boolean withdrawConfiguredGearSet(List<String> equipmentNames) {
        if (equipmentNames == null || equipmentNames.isEmpty()) {
            return true;
        }

        for (String equipmentName : equipmentNames) {
            if (equipmentName == null || equipmentName.isBlank()) {
                continue;
            }

            if (isWearingConfiguredEquipment(equipmentName) || hasInventoryEquipmentItem(equipmentName)) {
                continue;
            }

            boolean exact = requiresExactEquipmentMatch(equipmentName);
            if (!Rs2Bank.hasBankItem(equipmentName, 1, exact)) {
                stopWithReason("Missing gear: " + equipmentName + " was not equipped, not in inventory, and not found in the bank.");
                return false;
            }

            if (Rs2Inventory.isFull()) {
                stopWithReason("Inventory became full while withdrawing the configured gear set. Reduce gear/inventory setup size.");
                return false;
            }

            status = "Withdrawing gear set";
            if (!Rs2Bank.withdrawOne(equipmentName, exact)) {
                stopWithReason("Failed to withdraw gear: " + equipmentName + ".");
                return false;
            }

            if (!sleepUntil(() -> hasInventoryEquipmentItem(equipmentName), 3_500)) {
                stopWithReason("Gear did not appear in inventory after withdrawing: " + equipmentName + ".");
                return false;
            }
            sleep(180, 320);
        }

        return true;
    }

    private boolean equipConfiguredGearSet(List<String> equipmentNames) {
        if (equipmentNames == null || equipmentNames.isEmpty()) {
            return true;
        }

        status = "Equipping gear set";
        for (String equipmentName : equipmentNames) {
            if (!equipConfiguredItem(equipmentName)) {
                return false;
            }
            sleep(250, 450);
        }

        return true;
    }

    private boolean equipConfiguredItem(String equipmentName) {
        if (equipmentName == null || equipmentName.isBlank()) {
            return true;
        }

        if (isWearingConfiguredEquipment(equipmentName)) {
            return true;
        }

        boolean exact = requiresExactEquipmentMatch(equipmentName);

        if (Rs2Bank.isOpen()) {
            if (Rs2Bank.wearItem(equipmentName, exact)) {
                if (sleepUntil(() -> isWearingConfiguredEquipment(equipmentName), 3_500)) {
                    return true;
                }
            }
        }

        Rs2ItemModel item = getInventoryEquipmentItem(equipmentName);
        if (item != null) {
            for (String action : equipActionsFor(item)) {
                Rs2Inventory.interact(item, action);
                if (sleepUntil(() -> isWearingConfiguredEquipment(equipmentName), 2_500)) {
                    return true;
                }
                sleep(150, 300);
            }
        }

        stopWithReason("Failed to equip gear: " + equipmentName + ". Check that the item is wearable and the configured name is correct.");
        return false;
    }

    private List<String> equipActionsFor(Rs2ItemModel item) {
        List<String> actions = new ArrayList<>();
        if (item != null && item.getInventoryActions() != null) {
            for (String action : item.getInventoryActions()) {
                if (action == null) {
                    continue;
                }
                if (action.equalsIgnoreCase("Wear") || action.equalsIgnoreCase("Wield") || action.equalsIgnoreCase("Equip")) {
                    actions.add(action);
                }
            }
        }

        for (String fallback : new String[] {"Wear", "Wield", "Equip"}) {
            if (actions.stream().noneMatch(action -> action.equalsIgnoreCase(fallback))) {
                actions.add(fallback);
            }
        }
        return actions;
    }

    private boolean isWearingConfiguredEquipment(String equipmentName) {
        return Rs2Equipment.all()
                .anyMatch(item -> item != null && itemNameMatchesConfiguredEquipment(item.getName(), equipmentName));
    }

    private boolean hasInventoryEquipmentItem(String equipmentName) {
        return getInventoryEquipmentItem(equipmentName) != null;
    }

    private Rs2ItemModel getInventoryEquipmentItem(String equipmentName) {
        boolean exact = requiresExactEquipmentMatch(equipmentName);
        return Rs2Inventory.get(item -> item != null && itemNameMatches(item.getName(), equipmentName, exact));
    }

    private boolean itemNameMatchesConfiguredEquipment(String actualName, String configuredName) {
        return itemNameMatches(actualName, configuredName, requiresExactEquipmentMatch(configuredName));
    }

    private boolean itemNameMatches(String actualName, String configuredName, boolean exact) {
        if (actualName == null || configuredName == null) {
            return false;
        }

        String actual = actualName.trim().toLowerCase(Locale.ROOT);
        String configured = configuredName.trim().toLowerCase(Locale.ROOT);
        if (actual.isEmpty() || configured.isEmpty()) {
            return false;
        }

        return exact ? actual.equals(configured) : actual.contains(configured);
    }

    private boolean requiresExactEquipmentMatch(String equipmentName) {
        return equipmentName != null && equipmentName.trim().equalsIgnoreCase("Monk's robe");
    }

    private boolean hasInventoryAmount(String itemName, int amount) {
        return inventoryAmount(itemName) >= amount;
    }

    private int inventoryAmount(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return 0;
        }

        String name = itemName.trim().toLowerCase(Locale.ROOT);
        return Rs2Inventory.items(item -> item != null
                        && item.getName() != null
                        && item.getName().toLowerCase(Locale.ROOT).contains(name))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private boolean isBlightedKarambwanConfigured(KspZombiePirateKillerConfig config) {
        return configuredInventoryItems(config).stream()
                .anyMatch(item -> item != null && isBlightedKarambwanName(item.name));
    }

    private boolean hasBlightedKarambwan() {
        return Rs2Inventory.items(item -> item != null
                        && item.getName() != null
                        && isBlightedKarambwanName(item.getName()))
                .findAny()
                .isPresent();
    }

    private boolean isBlightedKarambwanName(String name) {
        if (name == null) {
            return false;
        }

        String lower = name.trim().toLowerCase(Locale.ROOT);
        return lower.contains("blighted") && lower.contains("karambwan");
    }

    private void stopWithReason(String reason) {
        if (hardStopped) {
            return;
        }

        hardStopped = true;
        state = KspZombiePirateKillerState.STOPPED;
        status = reason;
        Microbot.status = "KSP Zombie Pirates stopped: " + reason;
        log.error("[KSP Zombie Pirates] Stopping plugin: {}", reason);
        Microbot.showMessage("KSP Zombie Pirates stopped: " + reason);
        shutdown();
    }

    private void walkToZombiePirates(KspZombiePirateKillerConfig config) {
        state = KspZombiePirateKillerState.WALK_TO_PIRATES;

        if (!isReadyToEnterZombiePirates(config)) {
            status = "Loadout not ready, returning to Ferox";
            walkToFerox(config);
            return;
        }

        WorldPoint playerPoint = Rs2Player.getWorldLocation();
        if (!isAtZombiePiratePrayerActivationPoint(playerPoint)) {
            // Do not burn prayer from Ferox or during the long approach. Stop in the
            // configured northern entry rectangle before enabling prayer.
            status = "Walking to prayer activation area";
            Rs2Walker.walkTo(ZOMBIE_PIRATE_PRAYER_ACTIVATION_POINT);
            return;
        }

        // Prayer must be allowed to run before the final pathing step into the danger area,
        // but only after the configured gear and inventory setup is complete.
        if (shouldPreActivatePrayersWhileWalkingToPirates(config)) {
            status = "Activating prayers at entry area";
            ensureConfiguredPrayersActive(config);
        }

        status = "Walking into Zombie pirates";
        Rs2Walker.walkTo(ZOMBIE_PIRATE_CENTER);
    }

    private void prayAtNearestAltar(KspZombiePirateKillerConfig config) {
        state = KspZombiePirateKillerState.PRAYING_AT_ALTAR;
        status = "Restoring prayer at Chaos altar";

        final int realPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        if (Rs2Prayer.getPrayerPoints() >= realPrayer) {
            resumeCombatAfterPrayerRestore(config);
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - lastAltarAttempt < ALTAR_RETRY_DELAY_MS) {
            return;
        }
        lastAltarAttempt = now;

        // Do not explicitly walk to a stand tile first. Interact with the Chaos altar directly;
        // the game/client interaction can handle the short local pathing if needed.
        TileObject altar = findZombiePiratePrayerAltar();

        status = "Pray-at Chaos altar";
        boolean clicked = clickChaosAltar(altar);

        if (clicked) {
            // Keep this confirmation window short. If the player still needs to path to the altar,
            // the next script cycle can retry instead of blocking combat/loot decisions for seconds.
            if (sleepUntil(() -> Rs2Prayer.getPrayerPoints() >= realPrayer, ALTAR_CONFIRM_TIMEOUT_MS)) {
                lastAltarAttempt = 0L;
                resumeCombatAfterPrayerRestore(config);
            } else {
                status = "Moving to / using Chaos altar";
            }
            return;
        }

        status = "Chaos altar not clickable";
    }

    private boolean clickChaosAltar(TileObject altar) {
        if (altar != null) {
            if (Rs2GameObject.interact(altar, "Pray-at", true)
                    || Rs2GameObject.interact(altar, "Pray-at", false)
                    || Rs2GameObject.interact(altar, "Pray", true)
                    || Rs2GameObject.interact(altar, "Pray", false)) {
                return true;
            }
        }

        return Rs2GameObject.interact(PRAYER_ALTAR_ID, "Pray-at", 20)
                || Rs2GameObject.interact(PRAYER_ALTAR_ID, "Pray", 20)
                || Rs2GameObject.interact("Chaos altar", "Pray-at", true)
                || Rs2GameObject.interact("Chaos altar", "Pray-at", false)
                || Rs2GameObject.interact("Chaos altar", "Pray", true)
                || Rs2GameObject.interact("Chaos altar", "Pray", false)
                || Rs2GameObject.interact("Altar", "Pray-at", true)
                || Rs2GameObject.interact("Altar", "Pray-at", false)
                || Rs2GameObject.interact("Altar", "Pray", true)
                || Rs2GameObject.interact("Altar", "Pray", false);
    }

    private TileObject findZombiePiratePrayerAltar() {
        TileObject exact = Rs2GameObject.getTileObject(object ->
                        object != null
                                && object.getId() == PRAYER_ALTAR_ID
                                && PRAYER_ALTAR_POINT.equals(object.getWorldLocation()),
                PRAYER_ALTAR_POINT,
                10);

        if (exact != null) {
            return exact;
        }

        return Rs2GameObject.getTileObject(object ->
                        object != null
                                && object.getId() == PRAYER_ALTAR_ID
                                && object.getWorldLocation() != null
                                && object.getWorldLocation().distanceTo(PRAYER_ALTAR_POINT) <= 4,
                PRAYER_ALTAR_POINT,
                10);
    }

    private boolean ensureConfiguredPrayersActive(KspZombiePirateKillerConfig config) {
        List<Rs2PrayerEnum> configuredPrayers = configuredActivePrayers(config);
        boolean hasConfiguredPrayerWork = config.keepProtectMagic() || !configuredPrayers.isEmpty();

        if (Rs2Prayer.isOutOfPrayer()) {
            status = "Out of prayer";
            return !hasConfiguredPrayerWork;
        }

        boolean protectMagicRequested = config.keepProtectMagic() || configuredPrayers.contains(Rs2PrayerEnum.PROTECT_MAGIC);
        if (protectMagicRequested && !ensureProtectMagicActive()) {
            return false;
        }

        for (Rs2PrayerEnum prayer : configuredPrayers) {
            if (prayer == null || prayer == Rs2PrayerEnum.PROTECT_MAGIC) {
                continue;
            }

            if (!ensurePrayerActive(prayer, "Activating prayer: " + prayer.getName())) {
                return false;
            }
        }

        return true;
    }

    private List<Rs2PrayerEnum> configuredActivePrayers(KspZombiePirateKillerConfig config) {
        List<Rs2PrayerEnum> prayers = new ArrayList<>();
        for (String prayerName : parseNames(config.activePrayerNames())) {
            Rs2PrayerEnum prayer = parsePrayer(prayerName);
            if (prayer != null && !prayers.contains(prayer)) {
                prayers.add(prayer);
            }
        }

        return prayers;
    }

    private Rs2PrayerEnum parsePrayer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = normalizePrayerName(value);
        for (Rs2PrayerEnum prayer : Rs2PrayerEnum.values()) {
            if (normalizePrayerName(prayer.getName()).equals(normalized)
                    || normalizePrayerName(prayer.name()).equals(normalized)) {
                return prayer;
            }
        }

        log.warn("[KSP Zombie Pirates] Unknown configured prayer: {}", value);
        return null;
    }

    private String normalizePrayerName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("from", "")
                .replace("of", "")
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .trim();
    }

    private boolean ensureProtectMagicActive() {
        return ensurePrayerActive(Rs2PrayerEnum.PROTECT_MAGIC, "Activating Protect from Magic");
    }

    private boolean ensurePrayerActive(Rs2PrayerEnum prayer, String activatingStatus) {
        if (prayer == null) {
            return true;
        }

        if (Rs2Prayer.isOutOfPrayer()) {
            status = "Out of prayer";
            return false;
        }

        if (Rs2Prayer.isPrayerActive(prayer)) {
            return true;
        }

        state = KspZombiePirateKillerState.FIGHTING;
        status = activatingStatus;

        // First use direct component invocation. This does not require the Prayer tab/widget
        // to be open. If direct invocation fails, fall back to mouse mode, which opens the tab.
        if (Rs2Prayer.toggle(prayer, true, false)) {
            return true;
        }

        sleep(60, 120);
        if (Rs2Prayer.isPrayerActive(prayer)) {
            return true;
        }

        return Rs2Prayer.toggle(prayer, true, true);
    }

    private void resumeCombatAfterPrayerRestore(KspZombiePirateKillerConfig config) {
        if (config == null) {
            return;
        }

        if (Rs2Prayer.getPrayerPoints() < Microbot.getClient().getRealSkillLevel(Skill.PRAYER)) {
            return;
        }

        clearLastClickedNpc();
        lastAttackAttempt = 0L;
        lastLootAttempt = 0L;

        if (!ensureConfiguredPrayersActive(config)) {
            return;
        }

        if (shouldReturnToFerox(config)) {
            walkToFerox(config);
            return;
        }

        if (!isInZombiePirateArea()) {
            walkToZombiePirates(config);
            return;
        }

        // Do not force-walk back to a hardcoded center tile after using the altar.
        // The altar is inside the fight area, so resume target selection from the current tile.
        state = KspZombiePirateKillerState.FIGHTING;
        status = "Resuming Zombie pirate combat";
        attackZombiePirate(config);
    }

    private void resumeCombatAfterLooting(KspZombiePirateKillerConfig config) {
        clearLastClickedNpc();
        lastAttackAttempt = 0L;
        lastLootAttempt = 0L;

        // Do not wait for the full pickup movement. The next fast loop will decide whether
        // to loot again, attack, eat, or restore prayer. This keeps loot-to-attack latency low.
        sleep(35, 75);

        if (shouldReturnToFerox(config)) {
            walkToFerox(config);
            return;
        }

        if (!isInZombiePirateArea()) {
            walkToZombiePirates(config);
            return;
        }

        if (!ensureConfiguredPrayersActive(config)) {
            return;
        }

        status = "Resuming Zombie pirate combat";
        attackZombiePirate(config);
    }

    private void ensureLootingBagOpen() {
        Rs2ItemModel lootingBag = getLootingBag();
        if (lootingBag == null || !hasInventoryAction(lootingBag, "Open")) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - lastLootingBagOpenAttempt < 1_200) {
            return;
        }
        lastLootingBagOpenAttempt = now;

        state = KspZombiePirateKillerState.OPENING_LOOTING_BAG;
        status = "Opening looting bag";
        Rs2Inventory.interact(lootingBag, "Open");
        sleep(150, 260);
    }

    private Rs2ItemModel getLootingBag() {
        return Rs2Inventory.get(item -> item != null
                && item.getName() != null
                && item.getName().equalsIgnoreCase(LOOTING_BAG));
    }

    private boolean hasInventoryAction(Rs2ItemModel item, String action) {
        if (item == null || item.getInventoryActions() == null || action == null) {
            return false;
        }

        return Arrays.stream(item.getInventoryActions())
                .anyMatch(inventoryAction -> inventoryAction != null && inventoryAction.equalsIgnoreCase(action));
    }

    private boolean lootGroundItems(KspZombiePirateKillerConfig config) {
        if (lootingBagFull) {
            return false;
        }

        // Do not block looting simply because the player is in combat. Zombie pirate loot
        // should still be taken while the player is being attacked or fighting back.
        final long now = System.currentTimeMillis();
        if (now - lastLootAttempt < LOOT_RETRY_DELAY_MS) {
            return false;
        }
        lastLootAttempt = now;

        List<String> lootNameList = parseNames(config.lootNames());
        if (lootNameList.isEmpty()) {
            lootNameList = parseNames(KspZombiePirateKillerConfig.DEFAULT_LOOT_NAMES);
        }

        if (lootNameList.isEmpty()) {
            status = "Loot whitelist empty";
            return false;
        }

        int range = Math.max(1, config.lootRange());
        int minLootValue = Math.max(0, config.minLootValue());

        // Strict loot rule: only take whitelisted Zombie pirate drops when the actual
        // ground stack GE value is at or above the configured threshold. This prevents
        // low rolls such as small Gold ore / Adamant seed stacks from being taken below 1k.
        if (lootWhitelistedTileItemByStackValue(lootNameList, minLootValue, range)) {
            return true;
        }

        // Ground Items fallback keeps the same 1k+ value rule and ownership/anti-lure filtering.
        // It is value-based, so it will not pick up sub-threshold whitelist entries.
        LootingParameters valueLoot = new LootingParameters(
                Math.max(0, minLootValue - 1),
                Integer.MAX_VALUE,
                range,
                1,
                0,
                false,
                true,
                new String[] {"Bones", "Mind rune", "Looting bag", "Zombie champion scroll"}
        );

        return Rs2GroundItem.lootItemBasedOnValue(valueLoot);
    }

    private boolean lootWhitelistedTileItemByStackValue(List<String> lootNames, int minLootValue, int range) {
        RS2Item[] groundItems = Rs2GroundItem.getAll(range);
        if (groundItems == null || groundItems.length == 0) {
            return false;
        }

        for (RS2Item groundItem : groundItems) {
            if (groundItem == null || groundItem.getItem() == null || groundItem.getTileItem() == null) {
                continue;
            }

            String itemName = groundItem.getItem().getName();
            if (!isConfiguredLootName(itemName, lootNames)) {
                continue;
            }

            if (Rs2Inventory.isFull(itemName)) {
                continue;
            }

            long stackValue = groundStackGeValue(groundItem);
            if (stackValue < minLootValue) {
                continue;
            }

            if (!Rs2GroundItem.hasLineOfSight(groundItem.getTile())) {
                continue;
            }

            status = "Looting " + itemName + " (" + stackValue + " gp)";
            return Rs2GroundItem.interact(groundItem);
        }

        return false;
    }

    private long groundStackGeValue(RS2Item groundItem) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int itemId = groundItem.getItem().getId();
            int quantity = Math.max(1, groundItem.getTileItem().getQuantity());
            return (long) Microbot.getItemManager().getItemPrice(itemId) * quantity;
        }).orElse(0L);
    }

    private boolean isConfiguredLootName(String itemName, List<String> lootNames) {
        if (itemName == null || lootNames == null || lootNames.isEmpty()) {
            return false;
        }

        String normalizedItemName = itemName.trim().toLowerCase(Locale.ROOT);
        for (String configuredName : lootNames) {
            if (configuredName == null || configuredName.isBlank()) {
                continue;
            }

            String normalizedConfiguredName = configuredName.trim().toLowerCase(Locale.ROOT);
            if (normalizedItemName.equals(normalizedConfiguredName)
                    || normalizedItemName.contains(normalizedConfiguredName)) {
                return true;
            }
        }

        return false;
    }

    private void attackZombiePirate(KspZombiePirateKillerConfig config) {
        final long now = System.currentTimeMillis();
        if (now - lastAttackAttempt < ATTACK_RETRY_DELAY_MS) {
            return;
        }
        lastAttackAttempt = now;

        WorldPoint playerPoint = Rs2Player.getWorldLocation();
        if (playerPoint == null) {
            return;
        }

        Rs2NpcModel target = Rs2Npc.getNpcs(TARGET_NPC, true)
                .filter(this::isTargetZombiePirate)
                .filter(npc -> npc.getWorldLocation() != null)
                .filter(npc -> ZOMBIE_PIRATE_AREA.contains(npc.getWorldLocation()))
                .filter(npc -> npc.getCombatLevel() > 0 && !npc.isDead())
                .filter(npc -> npc.getWorldLocation().distanceTo(playerPoint) <= config.attackRange())
                .filter(npc -> !npc.isInteracting() || Objects.equals(npc.getInteracting(), Microbot.getClient().getLocalPlayer()))
                .filter(npc -> Rs2Npc.hasAction(npc.getId(), "Attack"))
                .min(Comparator.comparingInt(npc -> {
                    boolean attackingMe = Objects.equals(npc.getInteracting(), Microbot.getClient().getLocalPlayer());
                    int distance = npc.getWorldLocation().distanceTo(playerPoint);
                    return attackingMe ? distance - 1000 : distance;
                }))
                .orElse(null);

        if (target == null) {
            state = KspZombiePirateKillerState.FIGHTING;
            status = "No Zombie pirate target in range";
            return;
        }

        if (!isTargetZombiePirate(target)) {
            state = KspZombiePirateKillerState.FIGHTING;
            status = "Rejected non-Zombie target";
            log.warn("[KSP Zombie Pirates] Refusing to attack non-target NPC: {}", target.getName());
            return;
        }

        state = KspZombiePirateKillerState.FIGHTING;
        status = "Attacking Zombie pirate";
        if (Rs2Npc.interact(target, "Attack")) {
            lastClickedNpcIndex = target.getIndex();
            lastClickedNpcAt = System.currentTimeMillis();
            sleepUntil(() -> Rs2Player.isInCombat()
                    || Objects.equals(target.getInteracting(), Microbot.getClient().getLocalPlayer())
                    || isPlayerInteractingWithNpcIndex(lastClickedNpcIndex), ATTACK_CONFIRM_TIMEOUT_MS);
        }
    }

    private boolean isStillFightingLastClickedNpc() {
        if (lastClickedNpcIndex < 0) {
            return false;
        }

        Rs2NpcModel npc = getLastClickedNpc();
        if (!isValidAttackTarget(npc)) {
            clearLastClickedNpc();
            return false;
        }

        return isPlayerInteractingWithNpcIndex(lastClickedNpcIndex)
                || Objects.equals(npc.getInteracting(), Microbot.getClient().getLocalPlayer());
    }

    private void clearLastClickedNpcIfGone() {
        if (lastClickedNpcIndex < 0) {
            return;
        }

        if (!isValidAttackTarget(getLastClickedNpc())) {
            clearLastClickedNpc();
        }
    }

    private void clearLastClickedNpc() {
        lastClickedNpcIndex = -1;
        lastClickedNpcAt = 0L;
    }

    private Rs2NpcModel getLastClickedNpc() {
        if (lastClickedNpcIndex < 0) {
            return null;
        }

        return Rs2Npc.getNpcByIndex(lastClickedNpcIndex);
    }

    private boolean isValidAttackTarget(Rs2NpcModel npc) {
        return isTargetZombiePirate(npc)
                && npc.getWorldLocation() != null
                && ZOMBIE_PIRATE_AREA.contains(npc.getWorldLocation())
                && npc.getCombatLevel() > 0
                && !npc.isDead();
    }

    private boolean isPlayerInteractingWithNpcIndex(int npcIndex) {
        if (npcIndex < 0) {
            return false;
        }

        Rs2NpcModel npc = Rs2Npc.getNpcByIndex(npcIndex);
        if (npc == null) {
            return false;
        }

        Actor interacting = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
                return null;
            }
            return Microbot.getClient().getLocalPlayer().getInteracting();
        }).orElse(null);

        return interacting != null && interacting == npc.getRuneliteNpc();
    }

    private boolean isTargetZombiePirate(Rs2NpcModel npc) {
        if (npc == null || npc.getName() == null) {
            return false;
        }

        return TARGET_NPC.equalsIgnoreCase(npc.getName().trim());
    }

    private boolean hasFoodToEat(KspZombiePirateKillerConfig config) {
        return findFoodToEat(config) != null;
    }

    private boolean eatFoodIfNeeded(KspZombiePirateKillerConfig config) {
        if (config == null || Rs2Bank.isOpen()) {
            return false;
        }

        int hpPercent = currentHitpointsPercent();
        if (hpPercent > config.eatAtHp()) {
            return false;
        }

        final long now = System.currentTimeMillis();
        if (now - lastEatAttempt < 450) {
            state = KspZombiePirateKillerState.HEALING;
            status = "Waiting to eat (HP " + hpPercent + "%)";
            return true;
        }

        Rs2ItemModel food = findFoodToEat(config);
        if (food == null) {
            status = "Low HP, no edible food found";
            return false;
        }

        String foodName = food.getName() == null ? "food" : food.getName();
        int beforeHp = currentHitpoints();
        int beforeFoodAmount = inventoryAmount(foodName);

        lastEatAttempt = now;
        state = KspZombiePirateKillerState.HEALING;
        status = "Eating " + foodName + " at " + hpPercent + "% HP";

        // Rs2Inventory.interact opens the inventory tab internally and invokes the item menu,
        // so this is safe while the player is in combat or while another side tab is open.
        Rs2Inventory.open();
        boolean clicked = Rs2Inventory.interact(food, EAT_ACTION)
                || (food.getName() != null && Rs2Inventory.interact(food.getName(), EAT_ACTION, true))
                || (food.getName() != null && Rs2Inventory.interact(food.getName(), EAT_ACTION, false));

        if (!clicked) {
            lastEatAttempt = 0L;
            status = "Failed to click Eat on " + foodName;
            return false;
        }

        boolean consumed = sleepUntil(() -> currentHitpoints() > beforeHp
                        || inventoryAmount(foodName) < beforeFoodAmount,
                1_200);

        if (consumed) {
            lastEatAttempt = 0L;
            return true;
        }

        // If the first interaction did not actually consume the item, retry quickly on the next loop.
        lastEatAttempt = 0L;
        status = "Eat click did not consume " + foodName;
        return false;
    }

    private int currentHitpoints() {
        if (Microbot.getClient() == null) {
            return 0;
        }

        return Math.max(0, Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS));
    }

    private int currentHitpointsPercent() {
        if (Microbot.getClient() == null) {
            return 100;
        }

        int current = currentHitpoints();
        int real = Math.max(1, Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS));
        return Math.max(0, Math.min(100, (current * 100) / real));
    }

    private Rs2ItemModel findFoodToEat(KspZombiePirateKillerConfig config) {
        Rs2ItemModel blightedKarambwan = Rs2Inventory.get(item -> item != null
                && item.getName() != null
                && item.getName().equalsIgnoreCase(BLIGHTED_KARAMBWAN));
        if (blightedKarambwan != null) {
            return blightedKarambwan;
        }

        for (WithdrawItem configuredItem : parseWithdrawItems(config.inventoryItems())) {
            if (configuredItem == null || configuredItem.name == null || configuredItem.name.isBlank()) {
                continue;
            }

            Rs2ItemModel configuredFood = Rs2Inventory.get(item -> item != null
                    && item.getName() != null
                    && item.getName().equalsIgnoreCase(configuredItem.name)
                    && hasInventoryAction(item, EAT_ACTION));
            if (configuredFood != null) {
                return configuredFood;
            }
        }

        return Rs2Inventory.get(item -> item != null
                && item.getName() != null
                && hasInventoryAction(item, EAT_ACTION));
    }

    private boolean drinkPrayerRestore() {
        Rs2ItemModel potion = Rs2Inventory.items(item -> {
                    String name = item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
                    if (!Arrays.stream(item.getInventoryActions()).anyMatch(action -> action != null && action.equalsIgnoreCase("Drink"))) {
                        return false;
                    }
                    return name.contains("prayer potion")
                            || name.contains("super restore")
                            || name.contains("blighted super restore");
                })
                .findFirst()
                .orElse(null);

        if (potion == null) {
            return false;
        }

        return Rs2Inventory.interact(potion, "Drink");
    }

    private boolean hasPrayerRestore() {
        return Rs2Inventory.items(item -> {
                    String name = item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
                    return name.contains("prayer potion")
                            || name.contains("super restore")
                            || name.contains("blighted super restore");
                })
                .findAny()
                .isPresent();
    }

    private boolean isInFerox() {
        WorldPoint point = Rs2Player.getWorldLocation();
        return point != null && FEROX_AREA.contains(point);
    }

    private boolean isInZombiePirateArea() {
        WorldPoint point = Rs2Player.getWorldLocation();
        return point != null && ZOMBIE_PIRATE_AREA.contains(point);
    }

    private static WorldArea inclusiveArea(int x1, int y1, int x2, int y2, int plane) {
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        return new WorldArea(minX, minY, maxX - minX + 1, maxY - minY + 1, plane);
    }

    private static List<String> parseNames(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }

        return Arrays.stream(value.split("[,\n\r]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<WithdrawItem> configuredInventoryItems(KspZombiePirateKillerConfig config) {
        List<WithdrawItem> items = parseWithdrawItems(config.inventoryItems());
        if (!config.prayAtNearestAltar()) {
            return items;
        }

        return items.stream()
                .filter(item -> !isPrayerRestoreName(item.name))
                .collect(Collectors.toList());
    }

    private boolean isPrayerRestoreName(String name) {
        if (name == null) {
            return false;
        }

        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("blighted super restore")
                || lower.contains("super restore")
                || lower.contains("prayer potion");
    }

    private static List<WithdrawItem> parseWithdrawItems(String value) {
        List<WithdrawItem> items = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return items;
        }

        for (String raw : value.split("[\n\r]+")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split(":", 2);
            String name = parts[0].trim();
            int amount = 1;

            if (parts.length > 1) {
                try {
                    amount = Math.max(0, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }

            if (!name.isEmpty()) {
                items.add(new WithdrawItem(name, amount));
            }
        }

        return items;
    }

    private static final class WithdrawItem {
        private final String name;
        private final int amount;

        private WithdrawItem(String name, int amount) {
            this.name = name;
            this.amount = amount;
        }
    }
}
