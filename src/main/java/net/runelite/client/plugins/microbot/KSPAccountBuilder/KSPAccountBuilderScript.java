package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerConfig;
import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerRock;
import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerScript;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterConfig;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterScript;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterTree;
import net.runelite.client.plugins.microbot.autofishing.AutoFishingConfig;
import net.runelite.client.plugins.microbot.autofishing.AutoFishingScript;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.gecooker.GECookerConfig;
import net.runelite.client.plugins.microbot.gecooker.GECookerScript;
import net.runelite.client.plugins.microbot.gecooker.enums.CookingItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.runelite.api.coords.WorldPoint;

@Slf4j
public class KSPAccountBuilderScript extends Script {
    public static String status = "Idle";
    public static String stageLabel = "None";

    private static long startTimeMs;
    private static long stageStartTimeMs;
    private static long stageDurationMs;
    private final Random random = new Random();
    private long nextToolCheckMs;
    private static final long TOOL_CHECK_COOLDOWN_MS = 5000L;
    private static final WorldPoint KARAMJA_CUSTOMS_DOCK = new WorldPoint(2953, 3146, 0);
    private static final WorldPoint PORT_SARIM_DOCK = new WorldPoint(3027, 3217, 0);

    private Stage currentStage = Stage.NONE;
    private boolean minerRunning;
    private boolean woodcutterRunning;
    private boolean fishingRunning;
    private boolean cookerRunning;

    private static final List<ToolRequirement> PICKAXE_REQUIREMENTS = Arrays.asList(
            new ToolRequirement(ItemID.BRONZE_PICKAXE, 1, 1, Skill.MINING),
            new ToolRequirement(ItemID.IRON_PICKAXE, 1, 1, Skill.MINING),
            new ToolRequirement(ItemID.STEEL_PICKAXE, 6, 5, Skill.MINING),
            new ToolRequirement(ItemID.BLACK_PICKAXE, 11, 10, Skill.MINING),
            new ToolRequirement(ItemID.MITHRIL_PICKAXE, 21, 20, Skill.MINING),
            new ToolRequirement(ItemID.ADAMANT_PICKAXE, 31, 30, Skill.MINING),
            new ToolRequirement(ItemID.RUNE_PICKAXE, 41, 40, Skill.MINING),
            new ToolRequirement(ItemID.DRAGON_PICKAXE, 61, 60, Skill.MINING),
            new ToolRequirement(ItemID.INFERNAL_PICKAXE, 61, 60, Skill.MINING),
            new ToolRequirement(ItemID.CRYSTAL_PICKAXE, 71, 70, Skill.MINING)
    );

    private static final List<ToolRequirement> AXE_REQUIREMENTS = Arrays.asList(
            new ToolRequirement(ItemID.BRONZE_AXE, 1, 1, Skill.WOODCUTTING),
            new ToolRequirement(ItemID.IRON_AXE, 1, 1, Skill.WOODCUTTING),
            new ToolRequirement(ItemID.STEEL_AXE, 6, 5, Skill.WOODCUTTING),
            new ToolRequirement(ItemID.BLACK_AXE, 11, 10, Skill.WOODCUTTING),
            new ToolRequirement(ItemID.MITHRIL_AXE, 21, 20, Skill.WOODCUTTING),
            new ToolRequirement(ItemID.ADAMANT_AXE, 31, 30, Skill.WOODCUTTING),
            new ToolRequirement(ItemID.RUNE_AXE, 41, 40, Skill.WOODCUTTING),
            new ToolRequirement(ItemID.DRAGON_AXE, 61, 60, Skill.WOODCUTTING),
            new ToolRequirement(ItemID.INFERNAL_AXE, 61, 60, Skill.WOODCUTTING),
            new ToolRequirement(ItemID.CRYSTAL_AXE, 71, 70, Skill.WOODCUTTING)
    );

    @Inject
    private KSPAutoMinerScript minerScript;

    @Inject
    private KSPAutoWoodcutterScript woodcutterScript;

    @Inject
    private AutoFishingScript fishingScript;

    @Inject
    private GECookerScript cookerScript;

    @Inject
    private ConfigManager configManager;

    private enum Stage {
        MINING,
        WOODCUTTING,
        F2P_FISHING,
        F2P_COOKER,
        NONE
    }

    public boolean run(KSPAccountBuilderConfig config,
                       KSPAutoMinerConfig minerConfig,
                       KSPAutoWoodcutterConfig woodcutterConfig,
                       AutoFishingConfig fishingConfig,
                       GECookerConfig cookerConfig) {
        startTimeMs = System.currentTimeMillis();
        status = "Starting";
        KSPAccountBuilderStartSkill startSkill = config.startSkill();
        if (startSkill == KSPAccountBuilderStartSkill.RANDOM) {
            Stage[] startingStages = {Stage.MINING, Stage.WOODCUTTING, Stage.F2P_FISHING, Stage.F2P_COOKER};
            currentStage = startingStages[random.nextInt(startingStages.length)];
        } else {
            if (startSkill == KSPAccountBuilderStartSkill.WOODCUTTING) {
                currentStage = Stage.WOODCUTTING;
            } else if (startSkill == KSPAccountBuilderStartSkill.F2P_FISHING) {
                currentStage = Stage.F2P_FISHING;
            } else if (startSkill == KSPAccountBuilderStartSkill.F2P_COOKER) {
                currentStage = Stage.F2P_COOKER;
            } else {
                currentStage = Stage.MINING;
            }
        }
        stageLabel = currentStage.name();
        stageStartTimeMs = System.currentTimeMillis();
        stageDurationMs = selectStageDurationMs(config);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (!minerRunning && !woodcutterRunning && !fishingRunning && !cookerRunning) {
                    startStageIfNeeded(minerConfig, woodcutterConfig, fishingConfig, cookerConfig);
                }

                boolean miningComplete = isMiningComplete(config);
                boolean woodcuttingComplete = isWoodcuttingComplete(config);

                if (miningComplete && woodcuttingComplete) {
                    status = "Targets met";
                    stageLabel = "Complete";
                    stopAll();
                    if (config.stopWhenComplete()) {
                        shutdown();
                    }
                    return;
                }

                boolean shouldSwitchByTime = shouldSwitchByTime(config);

                if ((currentStage == Stage.MINING && (miningComplete || shouldSwitchByTime))
                        || (currentStage == Stage.WOODCUTTING && (woodcuttingComplete || shouldSwitchByTime))
                        || (currentStage == Stage.F2P_FISHING && shouldSwitchByTime)
                        || (currentStage == Stage.F2P_COOKER && shouldSwitchByTime)) {
                    if (handleKaramjaExitBeforeSwitch()) {
                        return;
                    }
                    stopCurrentStage();
                    currentStage = getNextStage(currentStage);
                    stageLabel = currentStage.name();
                    stageStartTimeMs = System.currentTimeMillis();
                    stageDurationMs = selectStageDurationMs(config);
                }

                if (currentStage == Stage.MINING && !miningComplete) {
                    status = "Training Mining";
                    startMiner(minerConfig);
                } else if (currentStage == Stage.WOODCUTTING && !woodcuttingComplete) {
                    status = "Training Woodcutting";
                    startWoodcutter(woodcutterConfig);
                } else if (currentStage == Stage.F2P_FISHING) {
                    status = "Training F2P Fishing";
                    startFishing(fishingConfig);
                } else if (currentStage == Stage.F2P_COOKER) {
                    status = "Training F2P Cooking";
                    startCooker(cookerConfig);
                }
            } catch (Exception ex) {
                Microbot.log("KSPAccountBuilder error: " + ex.getMessage());
            }
        }, 0, 800, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        stopAll();
        super.shutdown();
        status = "Stopped";
    }

    public static Duration getRuntime() {
        if (startTimeMs == 0) {
            return Duration.ZERO;
        }
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return Duration.ofMillis(elapsed);
    }

    public static Duration getTimeUntilSwitch() {
        if (stageStartTimeMs == 0 || stageDurationMs <= 0) {
            return Duration.ZERO;
        }
        long elapsed = System.currentTimeMillis() - stageStartTimeMs;
        long remaining = stageDurationMs - elapsed;
        if (remaining <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(remaining);
    }

    private boolean shouldSwitchByTime(KSPAccountBuilderConfig config) {
        long elapsedMs = System.currentTimeMillis() - stageStartTimeMs;
        return stageDurationMs > 0 && elapsedMs >= stageDurationMs;
    }

    private long selectStageDurationMs(KSPAccountBuilderConfig config) {
        int minMinutes = config.minSwitchMinutes();
        int maxMinutes = config.maxSwitchMinutes();
        if (minMinutes <= 0 || maxMinutes <= 0) {
            return 0L;
        }
        int lower = Math.min(minMinutes, maxMinutes);
        int upper = Math.max(minMinutes, maxMinutes);
        if (lower == upper) {
            return Duration.ofMinutes(lower).toMillis();
        }
        int chosenMinutes = lower + random.nextInt(upper - lower + 1);
        return Duration.ofMinutes(chosenMinutes).toMillis();
    }

    private void startStageIfNeeded(KSPAutoMinerConfig minerConfig,
                                    KSPAutoWoodcutterConfig woodcutterConfig,
                                    AutoFishingConfig fishingConfig,
                                    GECookerConfig cookerConfig) {
        if (currentStage == Stage.MINING) {
            startMiner(minerConfig);
        } else if (currentStage == Stage.WOODCUTTING) {
            startWoodcutter(woodcutterConfig);
        } else if (currentStage == Stage.F2P_FISHING) {
            startFishing(fishingConfig);
        } else if (currentStage == Stage.F2P_COOKER) {
            startCooker(cookerConfig);
        }
    }

    private Stage getNextStage(Stage stage) {
        if (stage == Stage.MINING) {
            return Stage.WOODCUTTING;
        }
        if (stage == Stage.WOODCUTTING) {
            return Stage.F2P_FISHING;
        }
        if (stage == Stage.F2P_FISHING) {
            return Stage.F2P_COOKER;
        }
        return Stage.MINING;
    }

    private boolean isMiningComplete(KSPAccountBuilderConfig config) {
        int level = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        return level >= config.targetMiningLevel();
    }

    private boolean isWoodcuttingComplete(KSPAccountBuilderConfig config) {
        int level = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        return level >= config.targetWoodcuttingLevel();
    }

    private void startMiner(KSPAutoMinerConfig minerConfig) {
        if (minerRunning) {
            return;
        }
        applyMiningRockForLevel();
        if (!ensureToolAvailable(true)) {
            status = "Missing pickaxe";
            return;
        }
        minerScript.run(minerConfig);
        minerRunning = true;
    }

    private void startWoodcutter(KSPAutoWoodcutterConfig woodcutterConfig) {
        if (woodcutterRunning) {
            return;
        }
        applyWoodcuttingTreeForLevel();
        if (!ensureToolAvailable(false)) {
            status = "Missing axe";
            return;
        }
        woodcutterScript.run(woodcutterConfig);
        woodcutterRunning = true;
    }

    private void stopMiner() {
        if (!minerRunning) {
            return;
        }
        minerScript.shutdown();
        minerRunning = false;
    }

    private void stopWoodcutter() {
        if (!woodcutterRunning) {
            return;
        }
        woodcutterScript.shutdown();
        woodcutterRunning = false;
    }

    private void stopAll() {
        stopMiner();
        stopWoodcutter();
        stopFishing();
        stopCooker();
    }

    private void stopCurrentStage() {
        if (currentStage == Stage.MINING) {
            stopMiner();
        } else if (currentStage == Stage.WOODCUTTING) {
            stopWoodcutter();
        } else if (currentStage == Stage.F2P_FISHING) {
            stopFishing();
        } else if (currentStage == Stage.F2P_COOKER) {
            stopCooker();
        }
    }

    private boolean handleKaramjaExitBeforeSwitch() {
        if (!isOnKaramja()) {
            return false;
        }

        status = "Leaving Karamja";
        Rs2NpcModel customsOfficer = Rs2Npc.getNpc("Customs officer");
        if (customsOfficer == null) {
            Rs2Walker.walkTo(KARAMJA_CUSTOMS_DOCK);
            return true;
        }

        int distance = customsOfficer.getWorldLocation().distanceTo(Rs2Player.getWorldLocation());
        if (distance > 5) {
            Rs2Walker.walkTo(customsOfficer.getWorldLocation());
            return true;
        }

        if (Rs2Npc.interact(customsOfficer, "Pay-fare")
                || Rs2Npc.interact(customsOfficer, "Pay fare")) {
            sleepUntil(() -> !isOnKaramja() || isNearPortSarimDock(), 8000);
        }
        return isOnKaramja() && !isNearPortSarimDock();
    }

    private boolean isOnKaramja() {
        if (Microbot.getClient().getLocalPlayer() == null) {
            return false;
        }
        var location = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return location.getX() >= 2760 && location.getX() <= 2965
                && location.getY() >= 3000 && location.getY() <= 3185
                && location.getPlane() == 0;
    }

    private boolean isNearPortSarimDock() {
        if (Microbot.getClient().getLocalPlayer() == null) {
            return false;
        }
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(PORT_SARIM_DOCK) <= 12;
    }

    private void applyMiningRockForLevel() {
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        List<KSPAutoMinerRock> availableRocks = Arrays.stream(KSPAutoMinerRock.values())
                .filter(rock -> miningLevel >= rock.getMiningLevel())
                .collect(java.util.stream.Collectors.toList());
        if (availableRocks.isEmpty()) {
            configManager.setConfiguration("KSPAutoMiner", "rock", KSPAutoMinerRock.COPPER_TIN);
            return;
        }
        KSPAutoMinerRock selected = availableRocks.get(random.nextInt(availableRocks.size()));
        configManager.setConfiguration("KSPAutoMiner", "rock", selected);
    }

    private void applyWoodcuttingTreeForLevel() {
        int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        List<KSPAutoWoodcutterTree> availableTrees = Arrays.stream(KSPAutoWoodcutterTree.values())
                .filter(tree -> woodcuttingLevel >= tree.getWoodcuttingLevel())
                .collect(java.util.stream.Collectors.toList());
        if (availableTrees.isEmpty()) {
            configManager.setConfiguration("KSPAutoWoodcutter", "tree", KSPAutoWoodcutterTree.TREE);
            return;
        }
        KSPAutoWoodcutterTree selected = availableTrees.get(random.nextInt(availableTrees.size()));
        configManager.setConfiguration("KSPAutoWoodcutter", "tree", selected);
    }

    private void applyFishingForLevel() {
        int fishingLevel = Microbot.getClient().getRealSkillLevel(Skill.FISHING);
        List<F2PFishOption> availableFish = Arrays.stream(F2PFishOption.values())
                .filter(option -> fishingLevel >= option.requiredLevel)
                .collect(java.util.stream.Collectors.toList());

        Fish selected = availableFish.isEmpty()
                ? Fish.SHRIMP_AND_ANCHOVIES
                : availableFish.get(random.nextInt(availableFish.size())).fish;

        configManager.setConfiguration("AutoFishing", "fishToCatch", selected);
    }

    private void startFishing(AutoFishingConfig fishingConfig) {
        if (fishingRunning) {
            return;
        }
        applyFishingForLevel();
        fishingScript.run(fishingConfig);
        fishingRunning = true;
    }

    private void stopFishing() {
        if (!fishingRunning) {
            return;
        }
        fishingScript.shutdown();
        fishingRunning = false;
    }


    private void applyCookingForLevel() {
        int cookingLevel = Microbot.getClient().getRealSkillLevel(Skill.COOKING);
        List<F2PCookOption> availableItems = Arrays.stream(F2PCookOption.values())
                .filter(option -> cookingLevel >= option.requiredLevel)
                .collect(java.util.stream.Collectors.toList());

        CookingItem selected = availableItems.isEmpty()
                ? CookingItem.RAW_SHRIMP
                : availableItems.get(random.nextInt(availableItems.size())).cookingItem;

        configManager.setConfiguration("GECooker", "Cook Item", selected);
    }

    private void startCooker(GECookerConfig cookerConfig) {
        if (cookerRunning) {
            return;
        }
        applyCookingForLevel();
        cookerScript.run(cookerConfig);
        cookerRunning = true;
    }

    private void stopCooker() {
        if (!cookerRunning) {
            return;
        }
        cookerScript.shutdown();
        cookerRunning = false;
    }

    private boolean ensureToolAvailable(boolean miningStage) {
        if (!Microbot.isLoggedIn() || Microbot.getClient().getLocalPlayer() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < nextToolCheckMs) {
            return false;
        }
        ToolRequirement requirement = getBestToolOnPlayer(miningStage);
        if (requirement != null) {
            nextToolCheckMs = 0L;
            return true;
        }
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.walkToBankAndUseBank()) {
                nextToolCheckMs = now + TOOL_CHECK_COOLDOWN_MS;
                return false;
            }
        }
        if (!Rs2Bank.isOpen()) {
            nextToolCheckMs = now + TOOL_CHECK_COOLDOWN_MS;
            return false;
        }
        Rs2ItemModel bestFromBank = getBestToolFromBank(miningStage);
        if (bestFromBank == null) {
            Rs2Bank.closeBank();
            nextToolCheckMs = now + TOOL_CHECK_COOLDOWN_MS;
            return false;
        }
        if (getAttackLevel(bestFromBank.getId(), miningStage) > 1 && canEquipTool(bestFromBank.getId(), miningStage)) {
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
        Rs2Inventory.waitForInventoryChanges(2000);
        boolean hasTool = getBestToolOnPlayer(miningStage) != null;
        if (hasTool) {
            Rs2Bank.closeBank();
            nextToolCheckMs = 0L;
            return true;
        }
        nextToolCheckMs = now + TOOL_CHECK_COOLDOWN_MS;
        return false;
    }

    private ToolRequirement getBestToolOnPlayer(boolean miningStage) {
        Rs2ItemModel equipped = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equipped != null && isValidTool(equipped.getId(), miningStage)) {
            return getRequirement(equipped.getId(), miningStage);
        }
        return Rs2Inventory.items()
                .filter(item -> isValidTool(item.getId(), miningStage))
                .max((first, second) -> Integer.compare(
                        getSkillLevel(first.getId(), miningStage),
                        getSkillLevel(second.getId(), miningStage)))
                .map(item -> getRequirement(item.getId(), miningStage))
                .orElse(null);
    }

    private Rs2ItemModel getBestToolFromBank(boolean miningStage) {
        return Rs2Bank.getAll(item -> isValidTool(item.getId(), miningStage))
                .max((first, second) -> Integer.compare(
                        getSkillLevel(first.getId(), miningStage),
                        getSkillLevel(second.getId(), miningStage)))
                .orElse(null);
    }

    private boolean isValidTool(int itemId, boolean miningStage) {
        ToolRequirement requirement = getRequirement(itemId, miningStage);
        if (requirement == null) {
            return false;
        }
        return Rs2Player.getSkillRequirement(requirement.skill, requirement.skillLevel);
    }

    private boolean canEquipTool(int itemId, boolean miningStage) {
        ToolRequirement requirement = getRequirement(itemId, miningStage);
        if (requirement == null) {
            return false;
        }
        return Rs2Player.getSkillRequirement(Skill.ATTACK, requirement.attackLevel);
    }

    private int getSkillLevel(int itemId, boolean miningStage) {
        ToolRequirement requirement = getRequirement(itemId, miningStage);
        return requirement != null ? requirement.skillLevel : 0;
    }

    private int getAttackLevel(int itemId, boolean miningStage) {
        ToolRequirement requirement = getRequirement(itemId, miningStage);
        return requirement != null ? requirement.attackLevel : 0;
    }

    private ToolRequirement getRequirement(int itemId, boolean miningStage) {
        List<ToolRequirement> requirements = miningStage ? PICKAXE_REQUIREMENTS : AXE_REQUIREMENTS;
        return requirements.stream()
                .filter(requirement -> requirement.itemId == itemId)
                .findFirst()
                .orElse(null);
    }

    private static final class ToolRequirement {
        private final int itemId;
        private final int skillLevel;
        private final int attackLevel;
        private final Skill skill;

        private ToolRequirement(int itemId, int skillLevel, int attackLevel, Skill skill) {
            this.itemId = itemId;
            this.skillLevel = skillLevel;
            this.attackLevel = attackLevel;
            this.skill = skill;
        }
    }

    private enum F2PFishOption {
        SHRIMP(Fish.SHRIMP_AND_ANCHOVIES, 1),
        SARDINE(Fish.SARDINE, 5),
        HERRING(Fish.HERRING, 10),
        TROUT_SALMON(Fish.TROUT_AND_SALMON, 20),
        LOBSTER(Fish.LOBSTER, 40),
        TUNA_SWORDFISH(Fish.TUNA_AND_SWORDFISH, 50);

        private final Fish fish;
        private final int requiredLevel;

        F2PFishOption(Fish fish, int requiredLevel) {
            this.fish = fish;
            this.requiredLevel = requiredLevel;
        }
    }

    private enum F2PCookOption {
        SHRIMP(CookingItem.RAW_SHRIMP, 1),
        HERRING(CookingItem.RAW_HERRING, 5),
        TROUT(CookingItem.RAW_TROUT, 15),
        SALMON(CookingItem.RAW_SALMON, 25),
        TUNA(CookingItem.RAW_TUNA, 30),
        LOBSTER(CookingItem.RAW_LOBSTER, 40),
        SWORDFISH(CookingItem.RAW_SWORDFISH, 45);

        private final CookingItem cookingItem;
        private final int requiredLevel;

        F2PCookOption(CookingItem cookingItem, int requiredLevel) {
            this.cookingItem = cookingItem;
            this.requiredLevel = requiredLevel;
        }
    }

}
