package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerConfig;
import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerRock;

import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerMode;


import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerMode;


import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerMode;


import net.runelite.client.plugins.microbot.KSPAutoMiner.KSPAutoMinerScript;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterConfig;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterScript;
import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterTree;

import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterMode;


import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterMode;


import net.runelite.client.plugins.microbot.KSPAutoWoodcutter.KSPAutoWoodcutterMode;


import net.runelite.client.plugins.microbot.autofishing.AutoFishingConfig;
import net.runelite.client.plugins.microbot.autofishing.AutoFishingScript;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.gecooker.GECookerConfig;
import net.runelite.client.plugins.microbot.gecooker.GECookerScript;
import net.runelite.client.plugins.microbot.gecooker.enums.CookingItem;

import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ClientUI;

import net.runelite.client.config.ConfigManager;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import net.runelite.client.plugins.Plugin;


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

    public static String currentMiningTask = "Unknown";
    public static String currentWoodcuttingTask = "Unknown";
    public static String currentFishingTask = "Unknown";
    public static String currentCookingTask = "Unknown";


    private static long startTimeMs;
    private static long stageStartTimeMs;
    private static long stageDurationMs;
    private final Random random = new Random();
    private long nextToolCheckMs;
    private static final long TOOL_CHECK_COOLDOWN_MS = 5000L;

    private static final int FISHING_SUPPLY_BUFFER = 150;
    private static final int FISHING_SUPPLY_BUY_AMOUNT = 1000;
    private static final WorldPoint KARAMJA_CUSTOMS_DOCK = new WorldPoint(2953, 3146, 0);
    private static final WorldPoint PORT_SARIM_DOCK = new WorldPoint(3027, 3217, 0);
    private static final List<SellFishOrder> RESTOCK_FISH_ORDERS = Arrays.asList(
            new SellFishOrder(361, "Tuna"),
            new SellFishOrder(351, "Pike"),
            new SellFishOrder(347, "Herring"),
            new SellFishOrder(329, "Salmon")
    );

    private static final WorldPoint KARAMJA_CUSTOMS_DOCK = new WorldPoint(2953, 3146, 0);
    private static final WorldPoint PORT_SARIM_DOCK = new WorldPoint(3027, 3217, 0);


    private Stage currentStage = Stage.NONE;
    private boolean minerRunning;
    private boolean woodcutterRunning;
    private boolean fishingRunning;
    private boolean cookerRunning;
    private F2PFishOption selectedF2PFishOption = F2PFishOption.SHRIMP;

    private static long nextBreakAtMs;
    private static long breakEndAtMs;
    private static boolean breakActive;
    private String originalClientTitle = "RuneLite";
    private long nextRelogAttemptAtMs;

    private long nextBreakAtMs;
    private long breakEndAtMs;
    private boolean breakActive;



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


    private KSPAutoMinerConfig minerConfig;
    private KSPAutoWoodcutterConfig woodcutterConfig;
    private AutoFishingConfig fishingConfig;
    private GECookerConfig cookerConfig;


    private enum Stage {
        MINING,
        WOODCUTTING,
        F2P_FISHING,
        F2P_COOKER,
        NONE
    }


    public boolean run(KSPAccountBuilderConfig config) {
        startTimeMs = System.currentTimeMillis();
        status = "Starting";
        resetCurrentTasks();
        this.minerConfig = configManager.getConfig(KSPAutoMinerConfig.class);
        this.woodcutterConfig = configManager.getConfig(KSPAutoWoodcutterConfig.class);
        this.fishingConfig = configManager.getConfig(AutoFishingConfig.class);
        this.cookerConfig = configManager.getConfig(GECookerConfig.class);
        captureOriginalClientTitle();

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

        scheduleNextBreak(config);



        scheduleNextBreak(config);

        scheduleNextBreak();




        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    return;
                }

                if (handleCustomBreak(config)) {
                    return;
                }

                if (!Microbot.isLoggedIn()) {
                    return;
                }


                if (!ensureCurrentTaskSupplies()) {
                    return;
                }

                if (!minerRunning && !woodcutterRunning && !fishingRunning && !cookerRunning) {
                    startStageIfNeeded();

                if (handleCustomBreak(config)) {

                if (handleCustomBreak()) {

                    return;
                }
                if (!minerRunning && !woodcutterRunning && !fishingRunning && !cookerRunning) {
                    startStageIfNeeded(minerConfig, woodcutterConfig, fishingConfig, cookerConfig);



                if (!minerRunning && !woodcutterRunning && !fishingRunning && !cookerRunning) {
                    startStageIfNeeded(minerConfig, woodcutterConfig, fishingConfig, cookerConfig);

                if (!minerRunning && !woodcutterRunning && !fishingRunning) {
                    startStageIfNeeded(minerConfig, woodcutterConfig, fishingConfig);

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

                if (shouldSwitchByTime) {

                    if (handleKaramjaExitBeforeSwitch()) {
                        return;
                    }
                    Stage nextStage = getNextStage(currentStage);
                    stopCurrentStage();
                    if (!prepareForStageSwitch(nextStage)) {
                        status = "Banking for switch";
                        return;
                    }
                    currentStage = nextStage;



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

                if (currentStage == Stage.MINING) {
                    status = "Training Mining";

                    startMiner();
                } else if (currentStage == Stage.WOODCUTTING) {
                    status = "Training Woodcutting";
                    startWoodcutter();
                } else if (currentStage == Stage.F2P_FISHING) {
                    status = "Training F2P Fishing";
                    startFishing();
                } else if (currentStage == Stage.F2P_COOKER) {
                    status = "Training F2P Cooking";
                    startCooker();

                    startMiner(minerConfig);
                } else if (currentStage == Stage.WOODCUTTING) {


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


    private void resetCurrentTasks() {
        currentMiningTask = "Unknown";
        currentWoodcuttingTask = "Unknown";
        currentFishingTask = "Unknown";
        currentCookingTask = "Unknown";
    }

    public static String getCurrentTaskSummary() {
        return String.join(", ", Arrays.asList(currentMiningTask, currentWoodcuttingTask, currentFishingTask, currentCookingTask));
    }


    @Override
    public void shutdown() {
        stopAll();
        super.shutdown();
        status = "Stopped";

        restoreClientTitle();

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


    public static Duration getTimeUntilNextBreak() {
        long now = System.currentTimeMillis();
        if (breakActive) {
            long remaining = breakEndAtMs - now;
            return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
        }
        if (nextBreakAtMs <= 0) {
            return Duration.ZERO;
        }
        long remaining = nextBreakAtMs - now;
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
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


    private boolean handleCustomBreak(KSPAccountBuilderConfig config) {
        if (!config.enableCustomBreaks()) {
            breakActive = false;
            nextBreakAtMs = 0L;
            breakEndAtMs = 0L;
            restoreClientTitle();
            return false;
        }


    private boolean handleCustomBreak(KSPAccountBuilderConfig config) {
        if (!config.enableCustomBreaks()) {
            breakActive = false;
            return false;
        }

    private boolean handleCustomBreak() {


        long now = System.currentTimeMillis();

        if (breakActive) {
            if (now < breakEndAtMs) {

                long remainingMs = breakEndAtMs - now;
                updateClientTitle(remainingMs);
                if (Microbot.isLoggedIn()) {
                    Rs2Player.logout();
                }

                status = "Custom break";
                return true;
            }
            breakActive = false;

            scheduleNextBreak(config);
            restoreClientTitle();
            if (!Microbot.isLoggedIn()) {
                status = "Break finished - logging in";
                attemptRelogAfterBreak();
                return true;
            }


            scheduleNextBreak(config);

            scheduleNextBreak();


            status = "Break finished";
            return false;
        }

        if (nextBreakAtMs > 0 && now >= nextBreakAtMs) {
            stopCurrentStage();
            breakActive = true;

            breakEndAtMs = now + selectBreakDurationMs(config);
            updateClientTitle(breakEndAtMs - now);
            if (Microbot.isLoggedIn()) {
                Rs2Player.logout();
            }


            breakEndAtMs = now + selectBreakDurationMs(config);

            breakEndAtMs = now + selectBreakDurationMs();


            status = "Custom break";
            return true;
        }

        return false;
    }


    private void scheduleNextBreak(KSPAccountBuilderConfig config) {
        if (!config.enableCustomBreaks()) {
            nextBreakAtMs = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        int minMinutes = config.minBreakIntervalMinutes();
        int maxMinutes = config.maxBreakIntervalMinutes();
        int lower = Math.min(minMinutes, maxMinutes);
        int upper = Math.max(minMinutes, maxMinutes);
        int chosenMinutes = lower == upper ? lower : lower + random.nextInt(upper - lower + 1);
        nextBreakAtMs = now + Duration.ofMinutes(chosenMinutes).toMillis();
    }

    private long selectBreakDurationMs(KSPAccountBuilderConfig config) {

        int minMinutes = config.minBreakDurationMinutes();
        int maxMinutes = config.maxBreakDurationMinutes();
        int lower = Math.min(minMinutes, maxMinutes);
        int upper = Math.max(minMinutes, maxMinutes);
        int chosenMinutes = lower == upper ? lower : lower + random.nextInt(upper - lower + 1);
        return Duration.ofMinutes(chosenMinutes).toMillis();
    }

    private void captureOriginalClientTitle() {
        try {
            if (ClientUI.getFrame() != null && ClientUI.getFrame().getTitle() != null) {
                originalClientTitle = ClientUI.getFrame().getTitle();
            }
        } catch (Exception ignored) {
        }
    }

    private void updateClientTitle(long remainingMs) {
        try {
            long totalSeconds = Math.max(0L, remainingMs / 1000L);
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            String remaining = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            if (ClientUI.getFrame() != null) {
                ClientUI.getFrame().setTitle(String.format("[Break %s] %s", remaining, originalClientTitle));
            }
        } catch (Exception ignored) {
        }
    }

    private void restoreClientTitle() {
        try {
            if (ClientUI.getFrame() != null) {
                ClientUI.getFrame().setTitle(originalClientTitle);
            }
        } catch (Exception ignored) {
        }
    }

    private void attemptRelogAfterBreak() {
        long now = System.currentTimeMillis();
        if (now < nextRelogAttemptAtMs) {
            return;
        }
        nextRelogAttemptAtMs = now + 5000L;

        try {
            Plugin autoLoginPlugin = (Plugin) Microbot.getPlugin("net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin");
            if (autoLoginPlugin == null) {
                return;
            }
            if (!Microbot.isPluginEnabled(autoLoginPlugin.getClass())) {
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Microbot.startPlugin(autoLoginPlugin);
                    return true;
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void startStageIfNeeded() {
        if (currentStage == Stage.MINING) {
            startMiner();
        } else if (currentStage == Stage.WOODCUTTING) {
            startWoodcutter();
        } else if (currentStage == Stage.F2P_FISHING) {
            startFishing();
        } else if (currentStage == Stage.F2P_COOKER) {
            startCooker();
        }
    }

    private boolean prepareForStageSwitch(Stage nextStage) {
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAll();

        if (nextStage == Stage.MINING) {
            boolean hasPickaxe = ensureToolAvailable(true);
            Rs2Bank.closeBank();
            return hasPickaxe;
        }

        if (nextStage == Stage.WOODCUTTING) {
            boolean hasAxe = ensureToolAvailable(false);
            Rs2Bank.closeBank();
            return hasAxe;
        }

        Rs2Bank.closeBank();
        return true;
    }

        int minSeconds = config.minBreakDurationSeconds();
        int maxSeconds = config.maxBreakDurationSeconds();
        int lower = Math.min(minSeconds, maxSeconds);
        int upper = Math.max(minSeconds, maxSeconds);
        int chosenSeconds = lower == upper ? lower : lower + random.nextInt(upper - lower + 1);
        return Duration.ofSeconds(chosenSeconds).toMillis();
    }


    private void scheduleNextBreak() {
        long now = System.currentTimeMillis();
        nextBreakAtMs = now + Duration.ofMinutes(6 + random.nextInt(7)).toMillis();
    }

    private long selectBreakDurationMs() {
        return Duration.ofSeconds(20 + random.nextInt(41)).toMillis();
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


    private void startMiner() {


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

        minerScript.run(this.minerConfig);
        minerRunning = true;
    }

    private void startWoodcutter() {

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

        woodcutterScript.run(this.woodcutterConfig);

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

    private boolean handleKaramjaEntryForFishing() {
        if (isOnKaramja()) {
            return false;
        }

        Rs2NpcModel customsOfficer = Rs2Npc.getNpc("Customs officer");
        if (customsOfficer == null) {
            Rs2Walker.walkTo(PORT_SARIM_DOCK);
            return true;
        }

        int distance = customsOfficer.getWorldLocation().distanceTo(Rs2Player.getWorldLocation());
        if (distance > 5) {
            Rs2Walker.walkTo(customsOfficer.getWorldLocation());
            return true;
        }

        if (Rs2Npc.interact(customsOfficer, "Pay-fare")
                || Rs2Npc.interact(customsOfficer, "Pay fare")) {
            sleepUntil(this::isOnKaramja, 8000);
        }
        return !isOnKaramja();
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
        configManager.setConfiguration("KSPAutoMiner", "mode", KSPAutoMinerMode.MINE_BANK);

        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        List<KSPAutoMinerRock> availableRocks = Arrays.stream(KSPAutoMinerRock.values())
                .filter(rock -> miningLevel >= rock.getMiningLevel())
                .collect(java.util.stream.Collectors.toList());
        if (availableRocks.isEmpty()) {
            configManager.setConfiguration("KSPAutoMiner", "rock", KSPAutoMinerRock.COPPER_TIN);

            currentMiningTask = KSPAutoMinerRock.COPPER_TIN.toString();
            return;
        }
        KSPAutoMinerRock selected = availableRocks.get(random.nextInt(availableRocks.size()));
        currentMiningTask = selected.toString();

            return;
        }
        KSPAutoMinerRock selected = availableRocks.get(random.nextInt(availableRocks.size()));

        configManager.setConfiguration("KSPAutoMiner", "rock", selected);
    }

    private void applyWoodcuttingTreeForLevel() {

        configManager.setConfiguration("KSPAutoWoodcutter", "mode", KSPAutoWoodcutterMode.PROGRESSIVE_BANK);


        configManager.setConfiguration("KSPAutoWoodcutter", "mode", KSPAutoWoodcutterMode.PROGRESSIVE_BANK);

        configManager.setConfiguration("KSPAutoWoodcutter", "mode", KSPAutoWoodcutterMode.CHOP_BANK);



        int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        List<KSPAutoWoodcutterTree> availableTrees = Arrays.stream(KSPAutoWoodcutterTree.values())
                .filter(tree -> woodcuttingLevel >= tree.getWoodcuttingLevel())
                .collect(java.util.stream.Collectors.toList());
        if (availableTrees.isEmpty()) {
            configManager.setConfiguration("KSPAutoWoodcutter", "tree", KSPAutoWoodcutterTree.TREE);

            currentWoodcuttingTask = KSPAutoWoodcutterTree.TREE.toString();
            return;
        }
        KSPAutoWoodcutterTree selected = availableTrees.get(random.nextInt(availableTrees.size()));
        currentWoodcuttingTask = selected.toString();

            return;
        }
        KSPAutoWoodcutterTree selected = availableTrees.get(random.nextInt(availableTrees.size()));

        configManager.setConfiguration("KSPAutoWoodcutter", "tree", selected);
    }

    private void applyFishingForLevel() {
        configManager.setConfiguration("AutoFishing", "useBank", true);

        int fishingLevel = Microbot.getClient().getRealSkillLevel(Skill.FISHING);
        List<F2PFishOption> availableFish = Arrays.stream(F2PFishOption.values())
                .filter(option -> fishingLevel >= option.requiredLevel)
                .collect(java.util.stream.Collectors.toList());

        selectedF2PFishOption = availableFish.isEmpty()
                ? F2PFishOption.SHRIMP
                : availableFish.get(random.nextInt(availableFish.size()));


        currentFishingTask = selectedF2PFishOption.getDisplayName();
        configManager.setConfiguration("AutoFishing", "fishToCatch", selectedF2PFishOption.fish);
    }

    private void startFishing() {

        configManager.setConfiguration("AutoFishing", "fishToCatch", selectedF2PFishOption.fish);


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

        if (!ensureFishingSupplies()) {
            status = "Restocking fishing supplies";
            return;
        }

        if (selectedF2PFishOption.requiresKaramja && handleKaramjaEntryForFishing()) {
            status = "Traveling to Karamja";
            return;
        }

        fishingScript.run(this.fishingConfig);

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


    private boolean ensureCurrentTaskSupplies() {
        if (currentStage != Stage.F2P_FISHING || fishingRunning) {
            return true;
        }
        return ensureFishingSupplies();
    }

    private boolean ensureFishingSupplies() {
        if (selectedF2PFishOption == null) {
            return true;
        }

        List<SupplyOrder> requiredSupplies = selectedF2PFishOption.getSupplyOrders();
        if (requiredSupplies.isEmpty()) {
            return true;
        }

        boolean hasSupplies = requiredSupplies.stream()
                .allMatch(supply -> (Rs2Inventory.count(supply.itemName) + countBankItem(supply.itemName)) >= FISHING_SUPPLY_BUFFER);
        if (hasSupplies) {
            return true;
        }

        status = "GE restock";
        return restockAtGrandExchange(requiredSupplies);
    }

    private int countBankItem(String itemName) {
        return Rs2Bank.bankItems().stream()
                .filter(item -> item.getName() != null && item.getName().equalsIgnoreCase(itemName))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private boolean restockAtGrandExchange(List<SupplyOrder> suppliesToBuy) {
        if (!Rs2GrandExchange.walkToGrandExchange()) {
            return false;
        }
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAll();
        Rs2Bank.setWithdrawAsNote();
        for (SellFishOrder fish : RESTOCK_FISH_ORDERS) {
            if (Rs2Bank.hasItem(fish.itemId)) {
                Rs2Bank.withdrawAll(fish.itemId);
            }
        }
        Rs2Bank.closeBank();

        if (!Rs2GrandExchange.openExchange()) {
            return false;
        }
        sleepUntil(Rs2GrandExchange::isOpen, 8000);

        for (SellFishOrder fish : RESTOCK_FISH_ORDERS) {
            int quantity = Rs2Inventory.count(fish.itemName, true);
            if (quantity <= 0) {
                continue;
            }
            int price = Math.max(1, Rs2GrandExchange.getPrice(fish.itemId));
            GrandExchangeRequest sellRequest = GrandExchangeRequest.builder()
                    .action(GrandExchangeAction.SELL)
                    .itemName(fish.itemName)
                    .quantity(quantity)
                    .price(price)
                    .closeAfterCompletion(false)
                    .build();
            Rs2GrandExchange.processOffer(sellRequest);
            sleepUntil(Rs2GrandExchange::isOpen, 4000);
        }

        for (SupplyOrder supply : suppliesToBuy) {
            int quantityNeeded = Math.max(0, FISHING_SUPPLY_BUY_AMOUNT - (Rs2Inventory.count(supply.itemName) + countBankItem(supply.itemName)));
            if (quantityNeeded <= 0) {
                continue;
            }
            GrandExchangeRequest buyRequest = GrandExchangeRequest.builder()
                    .action(GrandExchangeAction.BUY)
                    .itemName(supply.itemName)
                    .quantity(quantityNeeded)
                    .percent(5)
                    .closeAfterCompletion(false)
                    .build();
            Rs2GrandExchange.processOffer(buyRequest);
            sleepUntil(Rs2GrandExchange::isOpen, 4000);
        }

        if (Rs2GrandExchange.hasBoughtOffer() || Rs2GrandExchange.hasSoldOffer()) {
            Rs2GrandExchange.collectAllToBank();
            sleep(600, 1200);
        }
        Rs2GrandExchange.closeExchange();
        return true;
    }



    private void applyCookingForLevel() {
        int cookingLevel = Microbot.getClient().getRealSkillLevel(Skill.COOKING);
        List<F2PCookOption> availableItems = Arrays.stream(F2PCookOption.values())
                .filter(option -> cookingLevel >= option.requiredLevel)
                .collect(java.util.stream.Collectors.toList());

        CookingItem selected = availableItems.isEmpty()
                ? CookingItem.RAW_SHRIMP
                : availableItems.get(random.nextInt(availableItems.size())).cookingItem;


        currentCookingTask = formatCookingItemName(selected);
        configManager.setConfiguration("GECooker", "Cook Item", selected);
    }

    private String formatCookingItemName(CookingItem item) {
        if (item == null) {
            return "Unknown";
        }
        String readable = item.name().toLowerCase().replace("raw_", "").replace("_", " ");
        String[] words = readable.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.length() == 0 ? item.name() : result.toString();
    }

    private void startCooker() {

        configManager.setConfiguration("GECooker", "Cook Item", selected);
    }

    private void startCooker(GECookerConfig cookerConfig) {

        if (cookerRunning) {
            return;
        }
        applyCookingForLevel();

        cookerScript.run(this.cookerConfig);

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
        SHRIMP(Fish.SHRIMP_AND_ANCHOVIES, 1, false),

        SARDINE(Fish.SARDINE, 5, false, new SupplyOrder("Fishing bait")),
        HERRING(Fish.HERRING, 10, false, new SupplyOrder("Fishing bait")),
        TROUT_SALMON(Fish.TROUT_AND_SALMON, 20, false, new SupplyOrder("Feather")),
        PIKE(Fish.PIKE, 25, false, new SupplyOrder("Fishing bait")),

        SARDINE(Fish.SARDINE, 5, false),
        HERRING(Fish.HERRING, 10, false),
        TROUT_SALMON(Fish.TROUT_AND_SALMON, 20, false),
        PIKE(Fish.PIKE, 25, false),

        TUNA_SWORDFISH(Fish.TUNA_AND_SWORDFISH, 35, true),
        LOBSTER(Fish.LOBSTER, 40, true);

        private final Fish fish;
        private final int requiredLevel;
        private final boolean requiresKaramja;

        private final List<SupplyOrder> supplyOrders;
        private final String displayName;

        F2PFishOption(Fish fish, int requiredLevel, boolean requiresKaramja, SupplyOrder... supplyOrders) {
            this.fish = fish;
            this.requiredLevel = requiredLevel;
            this.requiresKaramja = requiresKaramja;
            this.supplyOrders = Arrays.asList(supplyOrders);
            this.displayName = buildDisplayName(name());
        }

        private List<SupplyOrder> getSupplyOrders() {
            return supplyOrders;
        }

        private String getDisplayName() {
            return displayName;
        }

        private static String buildDisplayName(String enumName) {
            String[] words = enumName.toLowerCase().split("_");
            StringBuilder value = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty() || "f2p".equals(word)) {
                    continue;
                }
                if (value.length() > 0) {
                    value.append(" ");
                }
                value.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return value.length() == 0 ? enumName : value.toString();
        }
    }

    private static final class SupplyOrder {
        private final String itemName;

        private SupplyOrder(String itemName) {
            this.itemName = itemName;
        }
    }

    private static final class SellFishOrder {
        private final int itemId;
        private final String itemName;

        private SellFishOrder(int itemId, String itemName) {
            this.itemId = itemId;
            this.itemName = itemName;


        F2PFishOption(Fish fish, int requiredLevel, boolean requiresKaramja) {
            this.fish = fish;
            this.requiredLevel = requiredLevel;
            this.requiresKaramja = requiresKaramja;


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
