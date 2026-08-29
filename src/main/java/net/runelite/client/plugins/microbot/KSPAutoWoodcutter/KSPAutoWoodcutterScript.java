package net.runelite.client.plugins.microbot.KSPAutoWoodcutter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.CustomWebWalker;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

@Slf4j
public class KSPAutoWoodcutterScript extends Script {
    private static final WorldPoint TREE_LOCATION = new WorldPoint(3160, 3453, 0);
    private static final WorldPoint OAK_LOCATION = new WorldPoint(3192, 3459, 0);
    private static final WorldPoint OAK_COMBAT_LOCATION = new WorldPoint(3099, 3243, 0);
    private static final WorldPoint WILLOW_LOCATION = new WorldPoint(3086, 3233, 0);
    private static final WorldPoint YEW_LOCATION = new WorldPoint(3085, 3475, 0);
    private static final List<String> ALL_LOGS = Arrays.asList(
            "Logs",
            "Oak logs",
            "Willow logs",
            "Teak logs",
            "Maple logs",
            "Mahogany logs",
            "Yew logs",
            "Magic logs",
            "Redwood logs"
    );

    private static final List<AxeRequirement> AXE_REQUIREMENTS = Arrays.asList(
            new AxeRequirement(ItemID.BRONZE_AXE, 1, 1),
            new AxeRequirement(ItemID.IRON_AXE, 1, 1),
            new AxeRequirement(ItemID.STEEL_AXE, 6, 5),
            new AxeRequirement(ItemID.BLACK_AXE, 11, 10),
            new AxeRequirement(ItemID.MITHRIL_AXE, 21, 20),
            new AxeRequirement(ItemID.ADAMANT_AXE, 31, 30),
            new AxeRequirement(ItemID.RUNE_AXE, 41, 40),
            new AxeRequirement(ItemID.DRAGON_AXE, 61, 60),
            new AxeRequirement(ItemID.INFERNAL_AXE, 61, 60),
            new AxeRequirement(ItemID.CRYSTAL_AXE, 71, 70)
    );

    public static String status = "Idle";
    public static String modeLabel = "";
    public static int logsCut = 0;

    private static long startTimeMs;
    private int lastInventoryCount;
    private KSPAutoWoodcutterTree selectedTree;
    private KSPAutoWoodcutterTree targetTree;
    private WorldPoint targetLocation;

    @Inject
    private Rs2TileObjectCache rs2TileObjectCache;

    public boolean run(KSPAutoWoodcutterConfig config) {
        startTimeMs = System.currentTimeMillis();
        logsCut = 0;
        lastInventoryCount = 0;
        status = "Starting";
        modeLabel = config.mode().toString();
        Rs2Antiban.resetAntibanSettings();
        if (config.enableAntiban()) {
            Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
            Rs2AntibanSettings.actionCooldownChance = 0.2;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (config.enableAntiban() && Rs2AntibanSettings.actionCooldownActive) {
                    return;
                }

                KSPAutoWoodcutterMode mode = config.mode();
                modeLabel = mode.toString();
                updateTarget(mode, config.tree());
                updateLogCount();

                if (targetTree == null || targetLocation == null) {
                    status = "No target tree";
                    return;
                }

                if (!hasRequiredLevel(targetTree)) {
                    status = "Woodcutting level too low";
                    return;
                }

                if (Rs2Inventory.isFull()) {
                    if (mode.isBankingMode()) {
                        status = "Banking";
                        if (!bankLogsAndUpgradeAxe(Rs2Player.getWorldLocation(), config.enableForestry())) {
                            return;
                        }
                    } else if (mode == KSPAutoWoodcutterMode.CHOP_BURN) {
                        status = "Burning logs";
                        burnLogs();
                    } else {
                        status = "Dropping logs";
                        dropLogs();
                    }
                    return;
                }

                if (Rs2Player.isAnimating()) {
                    return;
                }

                if (Rs2Player.getWorldLocation().distanceTo(targetLocation) > 6) {
                    status = "Walking to trees";
                    CustomWebWalker.walkTo(targetLocation);
                    return;
                }

                GameObject tree = findNearestTree(targetLocation, 12);
                if (tree == null) {
                    status = "No trees found";
                    return;
                }

                status = "Chopping " + targetTree.toString();
                if (Rs2GameObject.interact(tree)) {
                    Rs2Player.waitForXpDrop(Skill.WOODCUTTING, true);
                    if (config.enableAntiban()) {
                        Rs2Antiban.actionCooldown();
                        Rs2Antiban.takeMicroBreakByChance();
                    }
                }
            } catch (Exception ex) {
                Microbot.log("KSPAutoWoodcutter error: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        status = "Stopped";
        Rs2Antiban.resetAntibanSettings();
    }

    public static Duration getRuntime() {
        if (startTimeMs == 0) {
            return Duration.ZERO;
        }
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return Duration.ofMillis(elapsed);
    }

    private void updateTarget(KSPAutoWoodcutterMode mode, KSPAutoWoodcutterTree treeSelection) {
        int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        int combatLevel = Microbot.getClientThread().invoke(() -> {
            if (Microbot.getClient().getLocalPlayer() == null) {
                return 0;
            }
            return Microbot.getClient().getLocalPlayer().getCombatLevel();
        });
        KSPAutoWoodcutterTree previousTarget = targetTree;
        WorldPoint previousLocation = targetLocation;
        if (treeSelection != selectedTree) {
            selectedTree = treeSelection;
            resetLogCounters();
        }

        if (mode.isProgressiveMode()) {
            if (woodcuttingLevel >= 60) {
                targetTree = KSPAutoWoodcutterTree.YEW;
                targetLocation = YEW_LOCATION;
            } else if (woodcuttingLevel >= 30) {
                targetTree = KSPAutoWoodcutterTree.WILLOW;
                targetLocation = WILLOW_LOCATION;
            } else if (woodcuttingLevel >= 15) {
                targetTree = KSPAutoWoodcutterTree.OAK;
                targetLocation = combatLevel >= 40 ? OAK_COMBAT_LOCATION : OAK_LOCATION;
            } else {
                targetTree = KSPAutoWoodcutterTree.TREE;
                targetLocation = TREE_LOCATION;
            }
        } else {
            targetTree = treeSelection != null ? treeSelection : KSPAutoWoodcutterTree.TREE;
            targetLocation = Rs2Player.getWorldLocation();
        }

        if (previousTarget != targetTree || previousLocation != targetLocation) {
            resetLogCounters();
        }
    }

    private void updateLogCount() {
        int currentCount = countRelevantLogs();
        int delta = currentCount - lastInventoryCount;
        if (delta > 0) {
            logsCut += delta;
        }
        lastInventoryCount = currentCount;
    }

    private int countRelevantLogs() {
        if (targetTree == null) {
            return 0;
        }
        return Rs2Inventory.count(targetTree.getLogName());
    }

    private void resetLogCounters() {
        lastInventoryCount = countRelevantLogs();
    }

    private boolean bankLogsAndUpgradeAxe(WorldPoint returnLocation, boolean enableForestry) {
        if (!Rs2Bank.walkToBankAndUseBank()) {
            return false;
        }
        if (!Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAllExcept("axe");
        equipForestryKitIfAvailable(enableForestry);
        upgradeAxeIfAvailable();
        ensureAxeAvailable();
        Rs2Bank.closeBank();

        if (returnLocation != null) {
            CustomWebWalker.walkTo(returnLocation);
        }
        return true;
    }

    private void upgradeAxeIfAvailable() {
        Rs2ItemModel bestFromBank = getBestAxeFromBank();
        if (bestFromBank == null) {
            return;
        }

        Rs2ItemModel currentAxe = getBestAxeOnPlayer();
        int currentLevel = currentAxe != null ? getAxeWoodcuttingLevel(currentAxe.getId()) : 0;
        int bankLevel = getAxeWoodcuttingLevel(bestFromBank.getId());

        if (bankLevel <= currentLevel) {
            return;
        }

        if (getAxeAttackLevel(bestFromBank.getId()) > 1) {
            Rs2ItemModel currentWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            Rs2Bank.withdrawAndEquip(bestFromBank.getId());
            if (currentWeapon != null && currentWeapon.getId() != bestFromBank.getId()) {
                Rs2Bank.depositOne(currentWeapon.getId());
            }
        } else {
            if (!Rs2Inventory.hasItem(bestFromBank.getId())) {
                Rs2Bank.withdrawOne(bestFromBank.getId());
            }
        }

        if (currentAxe != null && currentAxe.getId() != bestFromBank.getId()
                && Rs2Inventory.hasItem(currentAxe.getId())) {
            Rs2Bank.depositOne(currentAxe.getId());
        }
    }

    private void ensureAxeAvailable() {
        if (getBestAxeOnPlayer() != null) {
            return;
        }
        Rs2ItemModel bestFromBank = getBestAxeFromBank();
        if (bestFromBank == null) {
            return;
        }
        Rs2Bank.withdrawOne(bestFromBank.getId());
    }

    private void equipForestryKitIfAvailable(boolean enableForestry) {
        if (!enableForestry) {
            return;
        }
        if (Rs2Equipment.isWearing(ItemID.FORESTRY_KIT)) {
            return;
        }
        if (!Rs2Bank.hasItem(ItemID.FORESTRY_KIT)) {
            return;
        }
        Rs2Bank.withdrawAndEquip(ItemID.FORESTRY_KIT);
    }

    private Rs2ItemModel getBestAxeOnPlayer() {
        Rs2ItemModel equipped = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equipped != null && isAxe(equipped.getId()) && canUseAxe(equipped.getId())) {
            return equipped;
        }

        return Rs2Inventory.items()
                .filter(item -> isAxe(item.getId()) && canUseAxe(item.getId()))
                .max((first, second) -> Integer.compare(
                        getAxeWoodcuttingLevel(first.getId()),
                        getAxeWoodcuttingLevel(second.getId())))
                .orElse(null);
    }

    private Rs2ItemModel getBestAxeFromBank() {
        return Rs2Bank.getAll(item -> isAxe(item.getId()) && canUseAxe(item.getId()))
                .max((first, second) -> Integer.compare(
                        getAxeWoodcuttingLevel(first.getId()),
                        getAxeWoodcuttingLevel(second.getId())))
                .orElse(null);
    }

    private boolean canUseAxe(int itemId) {
        AxeRequirement requirement = getAxeRequirement(itemId);
        return requirement != null
                && Rs2Player.getSkillRequirement(Skill.WOODCUTTING, requirement.woodcuttingLevel)
                && Rs2Player.getSkillRequirement(Skill.ATTACK, requirement.attackLevel);
    }

    private boolean isAxe(int itemId) {
        return getAxeRequirement(itemId) != null;
    }

    private int getAxeWoodcuttingLevel(int itemId) {
        AxeRequirement requirement = getAxeRequirement(itemId);
        return requirement != null ? requirement.woodcuttingLevel : 0;
    }

    private int getAxeAttackLevel(int itemId) {
        AxeRequirement requirement = getAxeRequirement(itemId);
        return requirement != null ? requirement.attackLevel : 0;
    }

    private AxeRequirement getAxeRequirement(int itemId) {
        return AXE_REQUIREMENTS.stream()
                .filter(requirement -> requirement.itemId == itemId)
                .findFirst()
                .orElse(null);
    }

    private GameObject findNearestTree(WorldPoint searchCenter, int radius) {
        if (searchCenter == null || targetTree == null) {
            return null;
        }

        return Rs2GameObject.getGameObjects(
                        Rs2GameObject.nameMatches(targetTree.getObjectName(), false),
                        searchCenter,
                        radius)
                .stream()
                .filter(Rs2GameObject::isReachable)
                .min((first, second) -> Integer.compare(
                        first.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()),
                        second.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
    }

    private void dropLogs() {
        for (String log : ALL_LOGS) {
            if (Rs2Inventory.hasItem(log, false)) {
                Rs2Inventory.dropAll(log);
            }
        }
    }

    private void burnLogs() {
        if (targetTree == null || !Rs2Inventory.hasItem(targetTree.getLogName())) {
            return;
        }

        Rs2TileObjectModel campfire = findActiveCampfire();
        if (campfire != null) {
            Rs2Inventory.useItemOnObject(targetTree.getLogId(), campfire.getId());
            Rs2Player.waitForXpDrop(Skill.FIREMAKING, true);
            return;
        }

        if (!Rs2Inventory.hasItem("Tinderbox")) {
            status = "Missing tinderbox";
            return;
        }

        Rs2Inventory.use("Tinderbox");
        Rs2Inventory.use(targetTree.getLogName());
        Rs2Player.waitForXpDrop(Skill.FIREMAKING, true);
    }

    private Rs2TileObjectModel findActiveCampfire() {
        Rs2TileObjectModel campfire = rs2TileObjectCache.query().where(tile -> tile.getId() == 49927).nearest(6);
        if (campfire != null) {
            return campfire;
        }
        return rs2TileObjectCache.query().where(tile -> tile.getId() == 26185).nearest(6);
    }

    private boolean hasRequiredLevel(KSPAutoWoodcutterTree tree) {
        return Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING) >= tree.getWoodcuttingLevel();
    }

    private static final class AxeRequirement {
        private final int itemId;
        private final int woodcuttingLevel;
        private final int attackLevel;

        private AxeRequirement(int itemId, int woodcuttingLevel, int attackLevel) {
            this.itemId = itemId;
            this.woodcuttingLevel = woodcuttingLevel;
            this.attackLevel = attackLevel;
        }
    }
}
