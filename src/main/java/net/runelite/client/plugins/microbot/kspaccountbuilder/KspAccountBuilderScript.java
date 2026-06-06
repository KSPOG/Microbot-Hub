package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.meleescript.MeleeScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.experiencelamps.KspExperienceLampScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.cookscript.CooksScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.reqs.Items;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.goblindipscript.GobScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.reqs.GobReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.fishingscript.FishingScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.levelreqfishing.LevelReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.firemakingscript.FireMakingScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.fmarea.FireArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.loglevels.LogsLvl;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript.MiningScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.BuyScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell.SellList;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript.SellScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smitharea.SmithArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithlevels.SmithLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithscript.SmithScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.oresreq.ReqOres;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea.SmeltArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltscript.SmeltScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.tutorialisland.TutorialIslandScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.util.autologin.AutoLoginScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas.TreeAreas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.woodcuttingscript.WoodCuttingScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.ClientUI;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class KspAccountBuilderScript extends Script
{
    private enum BuilderTask
    {
        TUTORIAL_ISLAND,
        COOKS_ASSISTANT,
        GOBLIN_DIPLOMACY,
        MINING,
        WOODCUTTING,
        FIREMAKING,
        FISHING,
        MELEE,
        GE_SELL,
        GE_BUY,
        SMITHING,
        SMELTING
    }

    private static final int LOOP_DELAY_MS = 600;
    private static final String EXTERNAL_AUTO_LOGIN_PLUGIN_CLASS = "net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin";
    private static final int POST_TUTORIAL_BANK_CAMERA_PITCH = 358;
    private static final int POST_TUTORIAL_BANK_CAMERA_YAW = 968;
    private static final int POST_TUTORIAL_BANK_CAMERA_SCALE = 706;
    private static final int SETTINGS_SIDE_INTERFACE_ID = 116;
    private static final int SETTINGS_ZOOM_3_WIDGET_ID = 49;
    private static final int CAMERA_ZOOM_TOLERANCE = 16;
    private static final int COINS_ID = 995;
    private static final String COINS = "Coins";
    private static final int MIN_QUEST_BUY_PRICE = 1_000;
    private static final String BLUE_GOBLIN_MAIL = "Blue goblin mail";
    private static final String ORANGE_GOBLIN_MAIL = "Orange goblin mail";
    private static final int CHICKEN_TARGET_COMBAT_STAT_LEVEL = 15;
    private static final String BRONZE_SWORD = "Bronze sword";
    private static final String WOODEN_SHIELD = "Wooden shield";

    @Inject
    private MiningScript miningScript;

    @Inject
    private WoodCuttingScript woodCuttingScript;

    @Inject
    private FireMakingScript fireMakingScript;

    @Inject
    private FishingScript fishingScript;

    @Inject
    private MeleeScript meleeScript;

    @Inject
    private BuyScript buyScript;

    @Inject
    private SellScript sellScript;

    @Inject
    private SmithScript smithScript;

    @Inject
    private SmeltScript smeltScript;

    @Inject
    private TutorialIslandScript tutorialIslandScript;

    @Inject
    private CooksScript cooksScript;

    @Inject
    private GobScript gobScript;

    @Inject
    private AutoLoginScript autoLoginScript;

    @Inject
    private KspExperienceLampScript experienceLampScript;

    @Getter
    private volatile BuilderTask currentTask;

    @Getter
    private volatile boolean breakActive;

    @Getter
    private long startedAtMillis;

    private boolean taskStarted;
    private long nextBreakAtMillis;
    private long breakEndsAtMillis;
    private long nextActivitySwitchAtMillis;
    private long lastStatusLogAt;
    private KspAccountBuilderConfig config;
    private boolean debugEnabled;
    private volatile BuilderTask pendingTask;
    private boolean pendingRandomTaskSelection;
    private boolean awaitingNextActivityStart;
    private boolean awaitingActivitySwitchTimerStart;
    private boolean breakLogoutRequested;
    private boolean postTutorialBankCameraPending;
    private volatile boolean shuttingDown;
    private long lastBreakLoginAttemptAt;
    private long lastLoginHandoffLogAt;
    private String originalWindowTitle = "Microbot";

    public boolean run(KspAccountBuilderConfig config)
    {
        shutdown();
        shuttingDown = false;
        this.config = config;
        this.debugEnabled = config.debugLogging();
        breakActive = false;
        stopExternalAutoLoginPlugin("builder-start");
        startAutoLoginHelper();
        if (experienceLampScript != null)
        {
            experienceLampScript.run();
        }
        miningScript.setDebugLogging(debugEnabled);
        woodCuttingScript.setDebugLogging(debugEnabled);
        fireMakingScript.setDebugLogging(debugEnabled);
        fishingScript.setDebugLogging(debugEnabled);
        meleeScript.setDebugLogging(debugEnabled);
        buyScript.setDebugLogging(debugEnabled);
        sellScript.setDebugLogging(debugEnabled);
        smithScript.setDebugLogging(debugEnabled);
        smeltScript.setDebugLogging(debugEnabled);
        tutorialIslandScript.setDebugLogging(debugEnabled);
        cooksScript.setDebugLogging(debugEnabled);
        gobScript.setDebugLogging(debugEnabled);
        applyAntibanSettings();

        currentTask = resolveStartingTask();
        taskStarted = false;
        breakActive = false;
        pendingTask = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
        breakLogoutRequested = false;
        postTutorialBankCameraPending = false;
        lastBreakLoginAttemptAt = 0L;
        lastLoginHandoffLogAt = 0L;
        captureOriginalWindowTitle();

        startedAtMillis = System.currentTimeMillis();
        lastStatusLogAt = 0L;

        scheduleNextBreak();
        nextActivitySwitchAtMillis = -1L;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!super.run())
                {
                    return;
                }

                debugEnabled = config.debugLogging();
                miningScript.setDebugLogging(debugEnabled);
                woodCuttingScript.setDebugLogging(debugEnabled);
                fireMakingScript.setDebugLogging(debugEnabled);
                fishingScript.setDebugLogging(debugEnabled);
                meleeScript.setDebugLogging(debugEnabled);
                buyScript.setDebugLogging(debugEnabled);
                sellScript.setDebugLogging(debugEnabled);
                smithScript.setDebugLogging(debugEnabled);
                smeltScript.setDebugLogging(debugEnabled);
                tutorialIslandScript.setDebugLogging(debugEnabled);
                cooksScript.setDebugLogging(debugEnabled);
                gobScript.setDebugLogging(debugEnabled);
                autoLoginScript.setDebugLogging(debugEnabled);

                processTimers();
                updateWindowTitle();
                if (breakActive || pendingTask != null)
                {
                    maybeLogStatus();
                    return;
                }

                if (!Microbot.isLoggedIn())
                {
                    maybeLogStatus();
                    return;
                }

                if (!isReadyAfterLoginHandoff())
                {
                    maybeLogStatus();
                    return;
                }

                runAccountBuilderCycle();
                maybeLogStatus();
            }
            catch (Exception ex)
            {
                log.error("[KSP Account Builder] Main account-builder loop failed; keeping scheduler alive", ex);
                taskStarted = false;
                pendingTask = null;
                pendingRandomTaskSelection = false;
                awaitingNextActivityStart = false;
                awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void processTimers()
    {
        if (shuttingDown)
        {
            return;
        }

        long now = System.currentTimeMillis();
        boolean singleSkillTaskForced = isSingleSkillTaskForced();

        if (config.doBreaks())
        {
            if (!breakActive && now >= nextBreakAtMillis)
            {
                breakActive = true;
                taskStarted = false;
                pendingTask = null;
                pendingRandomTaskSelection = false;
                awaitingNextActivityStart = false;
                breakLogoutRequested = false;
                lastBreakLoginAttemptAt = 0L;
                stopExternalAutoLoginPlugin("ksp-break-start");
                stopAutoLoginHelperForBreak();
                miningScript.shutdown();
                woodCuttingScript.shutdown();
                fireMakingScript.shutdown();
                fishingScript.shutdown();
                meleeScript.shutdown();
                buyScript.shutdown();
                sellScript.shutdown();
                smithScript.shutdown();
                smeltScript.shutdown();
                tutorialIslandScript.shutdown();
                cooksScript.shutdown();
                gobScript.shutdown();
                breakEndsAtMillis = now + TimeUnit.MINUTES.toMillis(randomMinutes(config.breakDurationMinMinutes(), config.breakDurationMaxMinutes()));
                debug("Starting break for {} seconds", getBreakTimeRemainingSeconds());
            }

            if (!shuttingDown && breakActive && Microbot.isLoggedIn() && !breakLogoutRequested)
            {
                Rs2Player.logout();
                breakLogoutRequested = true;
                sleepUntil(() -> !Microbot.isLoggedIn(), 5_000);
            }

            if (breakActive && now >= breakEndsAtMillis)
            {
                breakActive = false;
                breakLogoutRequested = false;
                scheduleNextBreak();
                debug("Break completed, resuming tasks");
                startAutoLoginHelper();

                if (!Microbot.isLoggedIn())
                {
                    attemptLoginAfterBreak();
                }
            }
        }

        if (singleSkillTaskForced)
        {
            pendingTask = null;
            pendingRandomTaskSelection = false;
            awaitingNextActivityStart = false;
            awaitingActivitySwitchTimerStart = false;
            nextActivitySwitchAtMillis = -1L;
            return;
        }

        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            pendingTask = null;
            pendingRandomTaskSelection = false;
            clearActivitySwitchTimerState();
            return;
        }

        if (!canUseActivitySwitchTimer() || awaitingNextActivityStart || awaitingActivitySwitchTimerStart)
        {
            return;
        }

        if (pendingTask == null && !pendingRandomTaskSelection && now >= nextActivitySwitchAtMillis)
        {
            if (!ensureInventoryTabOpenForTaskSelection())
            {
                return;
            }

            pendingRandomTaskSelection = true;
            taskStarted = false;
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            debug("Preparing activity switch from {}; next task will be selected after bank cleanup", currentTask);
        }

        if (pendingRandomTaskSelection && switchToRandomTaskAfterBank(currentTask))
        {
            pendingRandomTaskSelection = false;
            pendingTask = null;
            awaitingNextActivityStart = true;
            awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
            nextActivitySwitchAtMillis = -1L;
            return;
        }

        if (pendingTask != null && switchTask(pendingTask))
        {
            pendingTask = null;
            awaitingNextActivityStart = true;
            awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
            nextActivitySwitchAtMillis = -1L;
        }
    }

    private void startAutoLoginHelper()
    {
        if (autoLoginScript == null)
        {
            return;
        }

        stopExternalAutoLoginPlugin("ksp-helper-start");
        autoLoginScript.setDebugLogging(debugEnabled);
        autoLoginScript.run(() -> !breakActive);
    }

    private void stopAutoLoginHelperForBreak()
    {
        if (autoLoginScript == null)
        {
            return;
        }

        autoLoginScript.shutdown();
        debug("Paused KSP AutoLogin helper for break");
    }

    private void runAccountBuilderCycle()
    {
        if (postTutorialBankCameraPending)
        {
            setPostTutorialBankCamera();
        }

        applySingleSkillOverride();

        if (isSingleSkillTaskForced() && !hasResourcesForTask(currentTask))
        {
            Microbot.status = "Skipping " + currentTask + "; missing resources";
            stopCurrentTaskScript();
            taskStarted = false;
            debug("Single skill task {} is unavailable; skipping until requirements are met", currentTask);
            return;
        }

        if (!isSingleSkillTaskForced())
        {
            handleTutorialIslandPriority();
        }

        if (!isSingleSkillTaskForced() && !hasResourcesForTask(currentTask))
        {
            if (!switchToTaskWithResources())
            {
                taskStarted = false;
                return;
            }
        }

        if (taskStarted)
        {
            if (!isSingleSkillTaskForced()
                    && currentTask == BuilderTask.TUTORIAL_ISLAND
                    && tutorialIslandScript.isComplete())
            {
                tutorialIslandScript.shutdown();
                taskStarted = false;
                postTutorialBankCameraPending = true;
                setPostTutorialBankCamera();
                if (!switchToRandomTaskAfterBank(BuilderTask.TUTORIAL_ISLAND))
                {
                    stopCurrentTaskScript();
                }
                return;
            }

            if (!isSingleSkillTaskForced()
                    && currentTask == BuilderTask.COOKS_ASSISTANT
                    && cooksScript.isComplete())
            {
                cooksScript.shutdown();
                taskStarted = false;
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                }
                return;
            }

            if (!isSingleSkillTaskForced()
                    && currentTask == BuilderTask.GOBLIN_DIPLOMACY
                    && gobScript.isComplete())
            {
                gobScript.shutdown();
                taskStarted = false;
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                }
                return;
            }

            if (!isSingleSkillTaskForced() && currentTask == BuilderTask.GE_BUY && buyScript.isComplete())
            {
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                    taskStarted = false;
                }
                return;
            }
            if (!isSingleSkillTaskForced() && currentTask == BuilderTask.GE_SELL && sellScript.isComplete())
            {
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                    taskStarted = false;
                }
                return;
            }
            maybeStartActivitySwitchTimer();
            return;
        }

        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            clearActivitySwitchTimerState();
            taskStarted = tutorialIslandScript.run();
        }
        else if (currentTask == BuilderTask.COOKS_ASSISTANT)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            gobScript.shutdown();
            taskStarted = cooksScript.run();
        }
        else if (currentTask == BuilderTask.GOBLIN_DIPLOMACY)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            taskStarted = gobScript.run();
        }
        else if (currentTask == BuilderTask.MINING)
        {
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            miningScript.setProgressiveMining(isMiningProgressionEnabled());
            taskStarted = miningScript.run(resolveMiningStartArea());
        }
        else if (currentTask == BuilderTask.WOODCUTTING)
        {
            miningScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = woodCuttingScript.run(resolveWoodcuttingStartArea());
        }
        else if (currentTask == BuilderTask.FIREMAKING)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = fireMakingScript.run(FireArea.FM_AREA_DRAYNOR_BANK);
        }
        else if (currentTask == BuilderTask.FISHING)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = fishingScript.run(resolveFishingStartArea());
        }
        else if (currentTask == BuilderTask.MELEE)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = meleeScript.run();
        }
        else if (currentTask == BuilderTask.GE_SELL)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = sellScript.run(GEArea.GRAND_EXCHANGE);
        }
        else if (currentTask == BuilderTask.GE_BUY)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = buyScript.run(GEArea.GRAND_EXCHANGE);
        }
        else if (currentTask == BuilderTask.SMITHING)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = smithScript.run(SmithArea.SMITH_AREA_VARROCK_WEST_ANVIL);
        }
        else
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = smeltScript.run(SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE, resolveSmeltingFallbackBar());
        }

        if (taskStarted && awaitingNextActivityStart)
        {
            awaitingNextActivityStart = false;
            debug("Activity switch completed; waiting to reach task area before starting next switch timer");
        }

        maybeStartActivitySwitchTimer();
    }

    private boolean isReadyAfterLoginHandoff()
    {
        if (autoLoginScript != null && autoLoginScript.isActive())
        {
            debugLoginHandoffWait("autologin-helper-active");
            return false;
        }

        if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null)
        {
            debugLoginHandoffWait("local-player-null");
            return false;
        }

        if (getSafePlayerLocation() == null)
        {
            debugLoginHandoffWait("world-location-null");
            return false;
        }

        return true;
    }

    private void debugLoginHandoffWait(String reason)
    {
        long now = System.currentTimeMillis();
        if (now - lastLoginHandoffLogAt < 3_000)
        {
            return;
        }

        lastLoginHandoffLogAt = now;
        debug("Waiting after login before starting task | reason={} autoLoginState={} player={} taskStarted={} currentTask={}",
                reason,
                autoLoginScript == null ? "none" : autoLoginScript.getState(),
                getSafePlayerLocation(),
                taskStarted,
                currentTask);
    }

    private void handleTutorialIslandPriority()
    {
        if (!TutorialIslandScript.isOnTutorialIsland())
        {
            return;
        }

        if (currentTask != BuilderTask.TUTORIAL_ISLAND)
        {
            stopCurrentTaskScript();
            currentTask = BuilderTask.TUTORIAL_ISLAND;
            taskStarted = false;
            pendingTask = null;
            pendingRandomTaskSelection = false;
            awaitingNextActivityStart = false;
            awaitingActivitySwitchTimerStart = false;
            nextActivitySwitchAtMillis = -1L;
            debug("Detected Tutorial Island; switching to tutorial task");
        }

    }

    private BuilderTask resolveStartingTask()
    {
        if (TutorialIslandScript.isOnTutorialIsland())
        {
            return BuilderTask.TUTORIAL_ISLAND;
        }

        BuilderTask singleSkillTask = resolveSingleSkillTask();
        if (singleSkillTask != null)
        {
            return singleSkillTask;
        }

        BuilderTask taskWithResources = getRandomTaskWithResourcesExcluding(null);
        return taskWithResources != null ? taskWithResources : getRandomTaskExcluding(null);
    }

    private void applySingleSkillOverride()
    {
        if (!isSingleSkillTaskForced())
        {
            return;
        }

        BuilderTask singleSkillTask = resolveSingleSkillTask();
        if (singleSkillTask == null || currentTask == singleSkillTask)
        {
            return;
        }

        stopCurrentTaskScript();
        taskStarted = false;

        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to apply single skill override; still preparing bank handoff to {}", singleSkillTask);
            return;
        }

        if (!ensureInventoryTabOpenForTaskSelection())
        {
            debug("Waiting to apply single skill override; inventory tab is not open");
            return;
        }

        pendingTask = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = false;
        nextActivitySwitchAtMillis = -1L;
        currentTask = singleSkillTask;
        debug("Single skill override switched task to {}", currentTask);
    }

    private BuilderTask resolveSingleSkillTask()
    {
        if (config == null || !config.trainSingleSkill())
        {
            return null;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.TUTORIAL_ISLAND)
        {
            return BuilderTask.TUTORIAL_ISLAND;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.COOKS_ASSISTANT)
        {
            return BuilderTask.COOKS_ASSISTANT;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.GOBLIN_DIPLOMACY)
        {
            return BuilderTask.GOBLIN_DIPLOMACY;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.MINING)
        {
            return BuilderTask.MINING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.WOODCUTTING)
        {
            return BuilderTask.WOODCUTTING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.FIREMAKING)
        {
            return BuilderTask.FIREMAKING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.FISHING)
        {
            return BuilderTask.FISHING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.GE_SELL)
        {
            return BuilderTask.GE_SELL;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.MELEE)
        {
            return BuilderTask.MELEE;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.GE_BUY)
        {
            return BuilderTask.GE_BUY;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.SMELTING)
        {
            return BuilderTask.SMELTING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.SMITHING)
        {
            return BuilderTask.SMITHING;
        }

        return null;
    }

    private boolean isSingleSkillTaskForced()
    {
        return resolveSingleSkillTask() != null;
    }

    private boolean isActivitySwitchRandomizationEnabled()
    {
        return config != null
                && config.enableActivitySwitchRandomization()
                && !isSingleSkillTaskForced();
    }

    private boolean canUseActivitySwitchTimer()
    {
        return isActivitySwitchRandomizationEnabled()
                && currentTask != BuilderTask.TUTORIAL_ISLAND;
    }

    private void clearActivitySwitchTimerState()
    {
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = false;
        nextActivitySwitchAtMillis = -1L;
    }

    private boolean isMiningProgressionEnabled()
    {
        return !isSingleSkillTaskForced()
                || config.singleSkillTask() != KspTrainSingleSkillTask.MINING
                || config.singleSkillProgressive();
    }

    private Areas resolveMiningStartArea()
    {
        return Areas.TIN_COPPER_VARROCK_EAST;
    }

    private TreeAreas resolveWoodcuttingStartArea()
    {
        return TreeAreas.REGULAR_TREE_VARROCK_WEST;
    }

    private net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas resolveFishingStartArea()
    {
        return net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas.SHRIMP_ANCHOVIES;
    }

    private BarLevels resolveSmeltingFallbackBar()
    {
        return BarLevels.BRONZE;
    }

    private boolean hasResourcesForTask(BuilderTask task)
    {
        if (task == BuilderTask.TUTORIAL_ISLAND)
        {
            return TutorialIslandScript.isOnTutorialIsland();
        }

        if (task == BuilderTask.COOKS_ASSISTANT)
        {
            return !cooksScript.isComplete() && hasEnoughCoinsForCooksAssistant();
        }

        if (task == BuilderTask.GOBLIN_DIPLOMACY)
        {
            return !gobScript.isComplete() && hasEnoughCoinsForGoblinDiplomacy();
        }

        if (task == BuilderTask.MINING)
        {
            return hasAnyToolAvailable(Buy.PICKAXE_NAMES);
        }

        if (task == BuilderTask.WOODCUTTING)
        {
            return hasAnyToolAvailable(Buy.AXE_NAMES);
        }

        if (task == BuilderTask.FIREMAKING)
        {
            return hasAnyFiremakingResourcesAvailable();
        }

        if (task == BuilderTask.FISHING)
        {
            return hasAnyFishingResourcesAvailable();
        }

        if (task == BuilderTask.MELEE)
        {
            return hasAnyMeleeResourcesAvailable();
        }

        if (task == BuilderTask.GE_SELL)
        {
            return hasAnyGeSellResourcesAvailable();
        }

        if (task == BuilderTask.GE_BUY)
        {
            return hasAnyGeBuyResourcesAvailable();
        }

        if (task == BuilderTask.SMITHING)
        {
            return hasAnySmithingResourcesAvailable();
        }

        return hasAnySmeltingResourcesAvailable();
    }

    private boolean hasEnoughCoinsForCooksAssistant()
    {
        int missingItems = 0;
        for (Items item : Items.values())
        {
            if (countOwnedQuestItem(item.getDisplayName(), item.getItemId()) <= 0)
            {
                missingItems++;
            }
        }

        return hasCoinsForQuestBuys("Cook's Assistant", missingItems);
    }

    private boolean hasEnoughCoinsForGoblinDiplomacy()
    {
        int missingPlainMail = Math.max(0, getRequiredPlainGoblinMailCount() - countOwnedQuestItem(GobReqs.GOBLIN_MAIL.getDisplayName(), GobReqs.GOBLIN_MAIL.getItemId()));
        int missingBlueDye = countOwnedQuestItem(BLUE_GOBLIN_MAIL) > 0
                || countOwnedQuestItem(GobReqs.BLUE_DYE.getDisplayName(), GobReqs.BLUE_DYE.getItemId()) > 0 ? 0 : 1;
        int missingOrangeDye = countOwnedQuestItem(ORANGE_GOBLIN_MAIL) > 0
                || countOwnedQuestItem(GobReqs.ORANGE_DYE.getDisplayName(), GobReqs.ORANGE_DYE.getItemId()) > 0 ? 0 : 1;

        return hasCoinsForQuestBuys("Goblin Diplomacy", missingPlainMail + missingBlueDye + missingOrangeDye);
    }

    private int getRequiredPlainGoblinMailCount()
    {
        int requiredMail = 1;

        if (countOwnedQuestItem(BLUE_GOBLIN_MAIL) <= 0)
        {
            requiredMail++;
        }

        if (countOwnedQuestItem(ORANGE_GOBLIN_MAIL) <= 0)
        {
            requiredMail++;
        }

        return requiredMail;
    }

    private boolean hasCoinsForQuestBuys(String questName, int missingItems)
    {
        if (missingItems <= 0)
        {
            return true;
        }

        long requiredCoins = (long) missingItems * MIN_QUEST_BUY_PRICE;
        long availableCoins = getAvailableCoins();
        boolean enoughCoins = availableCoins >= requiredCoins;
        if (!enoughCoins)
        {
            debug("Skipping {} due to insufficient GP | missingItems={} availableCoins={} requiredCoins={}",
                    questName,
                    missingItems,
                    availableCoins,
                    requiredCoins);
        }
        return enoughCoins;
    }

    private int countOwnedQuestItem(String itemName)
    {
        return Rs2Inventory.count(itemName)
                + Rs2Inventory.count(itemName, true)
                + Math.max(0, Rs2Bank.count(itemName));
    }

    private int countOwnedQuestItem(String itemName, int itemId)
    {
        return Rs2Inventory.itemQuantity(itemId)
                + Math.max(0, Rs2Bank.count(itemName));
    }

    private long getAvailableCoins()
    {
        return Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID))
                + Math.max(0L, Rs2Bank.count(COINS));
    }

    private boolean hasAnyToolAvailable(String[] toolNames)
    {
        for (String toolName : toolNames)
        {
            if (Rs2Equipment.isWearing(toolName) || Rs2Inventory.hasItem(toolName) || Rs2Bank.count(toolName) > 0)
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyMeleeResourcesAvailable()
    {
        return hasRequiredMeleeGearAvailable();
    }

    private boolean hasRequiredMeleeGearAvailable()
    {
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        int strengthLevel = Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
        int defenceLevel = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);

        if (attackLevel < CHICKEN_TARGET_COMBAT_STAT_LEVEL
                || strengthLevel < CHICKEN_TARGET_COMBAT_STAT_LEVEL
                || defenceLevel < CHICKEN_TARGET_COMBAT_STAT_LEVEL)
        {
            return hasMeleeItemAvailable(BRONZE_SWORD) && hasMeleeItemAvailable(WOODEN_SHIELD);
        }

        Buy.MeleeGearPlan gearPlan = Buy.buildMeleeGearPlan(
                attackLevel,
                defenceLevel,
                Rs2Player.getQuestState(Quest.DRAGON_SLAYER_I) == QuestState.FINISHED,
                this::hasMeleeItemAvailable);

        if (!gearPlan.getMissingItems().isEmpty())
        {
            debug("Skipping Melee; required gear is missing | missing={}", gearPlan.getMissingItems());
            return false;
        }

        return !gearPlan.getDesiredItems().isEmpty();
    }

    private boolean hasMeleeItemAvailable(String itemName)
    {
        return itemName != null
                && (Rs2Equipment.isWearing(itemName)
                || Rs2Inventory.hasItem(itemName)
                || Rs2Inventory.hasItem(itemName, true)
                || Rs2Bank.count(itemName) > 0);
    }

    private boolean hasBankItem(String itemName)
    {
        return itemName != null && Rs2Bank.count(itemName) > 0;
    }

    private boolean hasAnySmeltingResourcesAvailable()
    {
        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        for (int i = BarLevels.values().length - 1; i >= 0; i--)
        {
            BarLevels bar = BarLevels.values()[i];
            if (smithingLevel < bar.getRequiredSmithingLevel())
            {
                continue;
            }

            ReqOres req = ReqOres.valueOf(bar.name());
            int primaryAvailable = Rs2Inventory.count(req.getPrimaryOreName()) + Math.max(0, Rs2Bank.count(req.getPrimaryOreName()));
            if (primaryAvailable < req.getPrimaryOreAmount())
            {
                continue;
            }

            if (!req.hasSecondaryOre())
            {
                return true;
            }

            int secondaryAvailable = Rs2Inventory.count(req.getSecondaryOreName()) + Math.max(0, Rs2Bank.count(req.getSecondaryOreName()));
            if (secondaryAvailable >= req.getSecondaryOreAmount())
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyFiremakingResourcesAvailable()
    {
        if (!hasTinderboxAnywhere())
        {
            return false;
        }

        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        for (int i = LogsLvl.values().length - 1; i >= 0; i--)
        {
            LogsLvl logsLvl = LogsLvl.values()[i];
            if (firemakingLevel < logsLvl.getRequiredLevel())
            {
                continue;
            }

            int availableLogs = Rs2Inventory.count(logsLvl.getDisplayName()) + Math.max(0, Rs2Bank.count(logsLvl.getDisplayName()));
            if (availableLogs > 0)
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyFishingResourcesAvailable()
    {
        int fishingLevel = Microbot.getClient().getRealSkillLevel(Skill.FISHING);
        LevelReqs targetFish = LevelReqs.bestForFishingLevel(fishingLevel);
        net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.needed.Inventory requiredInventory =
                net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.needed.Inventory.valueOf(targetFish.name());

        for (String itemName : requiredInventory.getRequiredItems())
        {
            int available = Rs2Inventory.count(itemName)
                    + Rs2Inventory.count(itemName, true)
                    + Math.max(0, Rs2Bank.count(itemName));
            if (available <= 0)
            {
                return false;
            }
        }

        return true;
    }

    private boolean hasAnySmithingResourcesAvailable()
    {
        if (!hasHammerWithRequiredIdAnywhere())
        {
            return false;
        }

        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        for (int i = SmithLevels.values().length - 1; i >= 0; i--)
        {
            SmithLevels level = SmithLevels.values()[i];
            if (smithingLevel < level.getRequiredLevel())
            {
                continue;
            }

            String barName = level.getDisplayName().split(" ")[0] + " bar";
            int barsAvailable = Rs2Inventory.count(barName) + Math.max(0, Rs2Bank.count(barName));
            if (barsAvailable > 0)
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyGeSellResourcesAvailable()
    {
        if (hasAnySellListItemsAvailable())
        {
            return true;
        }

        if (hasAnyOutdatedToolAvailable())
        {
            return true;
        }

        return false;
    }

    private boolean hasAnyGeBuyResourcesAvailable()
    {
        return Buy.hasAnyGeBuyRequirementMissing();
    }

    private boolean hasAnySellListItemsAvailable()
    {
        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        for (SellList sellList : SellList.values())
        {
            if (sellList == SellList.LOGS && firemakingLevel < 15)
            {
                continue;
            }

            if (sellList == SellList.OAK_LOGS && firemakingLevel < 30)
            {
                continue;
            }

            int available = Rs2Inventory.count(sellList.getDisplayName())
                    + Rs2Inventory.count(sellList.getDisplayName(), true)
                    + Math.max(0, Rs2Bank.count(sellList.getDisplayName()));
            if (available > 0)
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyOutdatedToolAvailable()
    {
        String desiredPickaxe = resolveDesiredPickaxeForSellingTask();
        String desiredAxe = resolveDesiredAxeForSellingTask();

        for (String pickaxeName : Buy.PICKAXE_NAMES)
        {
            if (pickaxeName.equalsIgnoreCase(desiredPickaxe))
            {
                continue;
            }

            if (Rs2Equipment.isWearing(pickaxeName)
                    || Rs2Inventory.hasItem(pickaxeName)
                    || Rs2Inventory.hasItem(pickaxeName, true)
                    || Rs2Bank.count(pickaxeName) > 0)
            {
                return true;
            }
        }

        for (String axeName : Buy.AXE_NAMES)
        {
            if (axeName.equalsIgnoreCase(desiredAxe))
            {
                continue;
            }

            if (Rs2Equipment.isWearing(axeName)
                    || Rs2Inventory.hasItem(axeName)
                    || Rs2Inventory.hasItem(axeName, true)
                    || Rs2Bank.count(axeName) > 0)
            {
                return true;
            }
        }

        return false;
    }

    private String resolveDesiredPickaxeForSellingTask()
    {
        return Buy.resolveDesiredPickaxeNameForGear();
    }

    private String resolveDesiredAxeForSellingTask()
    {
        return Buy.resolveDesiredAxeNameForGear();
    }

    private boolean hasHammerWithRequiredIdAnywhere()
    {
        return Buy.hasHammerAnywhere();
    }

    private boolean hasTinderboxAnywhere()
    {
        return Buy.hasTinderboxAnywhere();
    }

    private boolean switchToTaskWithResources()
    {
        stopCurrentTaskScript();
        taskStarted = false;

        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to switch task; still preparing bank handoff before selecting replacement for {}", currentTask);
            return false;
        }

        BuilderTask nextTask = getRandomTaskWithResourcesExcluding(currentTask);
        if (nextTask == null)
        {
            debug("Current task {} has no resources and no alternative task is available after bank cleanup", currentTask);
            return false;
        }

        if (!ensureInventoryTabOpenForTaskSelection())
        {
            debug("Waiting to switch task; inventory tab is not open before switching from {} to {}", currentTask, nextTask);
            return false;
        }

        pendingTask = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        debug("Switching task from {} to {} because resources are unavailable", currentTask, nextTask);
        currentTask = nextTask;
        awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
        nextActivitySwitchAtMillis = -1L;
        return true;
    }

    private boolean switchToRandomTaskAfterBank(BuilderTask excludedTask)
    {
        stopCurrentTaskScript();
        taskStarted = false;

        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to select next task; still preparing bank handoff from {}", currentTask);
            return false;
        }

        BuilderTask nextTask = getRandomTaskWithResourcesExcluding(excludedTask);
        if (nextTask == null)
        {
            nextTask = getRandomTaskExcluding(excludedTask);
        }

        if (nextTask == null)
        {
            debug("No next task could be selected after bank cleanup | excluded={}", excludedTask);
            return false;
        }

        if (!ensureInventoryTabOpenForTaskSelection())
        {
            debug("Waiting to select next task; inventory tab is not open before switching from {} to {}", currentTask, nextTask);
            return false;
        }

        pendingTask = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        currentTask = nextTask;
        awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
        nextActivitySwitchAtMillis = -1L;
        debug("Selected next task after bank cleanup | currentTask={}", currentTask);
        return true;
    }

    private BuilderTask getRandomTaskWithResourcesExcluding(BuilderTask excludedTask)
    {
        if (TutorialIslandScript.isOnTutorialIsland())
        {
            return excludedTask == BuilderTask.TUTORIAL_ISLAND ? null : BuilderTask.TUTORIAL_ISLAND;
        }

        BuilderTask primaryTask = getRandomTaskWithResourcesExcluding(excludedTask, false);
        if (primaryTask != null)
        {
            return primaryTask;
        }

        return getRandomTaskWithResourcesExcluding(excludedTask, true);
    }

    private BuilderTask getRandomTaskWithResourcesExcluding(BuilderTask excludedTask, boolean includeSupportTasks)
    {
        if (TutorialIslandScript.isOnTutorialIsland())
        {
            return excludedTask == BuilderTask.TUTORIAL_ISLAND ? null : BuilderTask.TUTORIAL_ISLAND;
        }

        BuilderTask[] candidates = new BuilderTask[BuilderTask.values().length];
        int candidateCount = 0;

        for (BuilderTask task : BuilderTask.values())
        {
            if (task == excludedTask || task == BuilderTask.TUTORIAL_ISLAND)
            {
                continue;
            }

            if (!includeSupportTasks && isSupportTask(task))
            {
                continue;
            }

            if (hasResourcesForTask(task))
            {
                candidates[candidateCount++] = task;
            }
        }

        if (candidateCount == 0)
        {
            return null;
        }

        return candidates[ThreadLocalRandom.current().nextInt(candidateCount)];
    }

    private boolean isSupportTask(BuilderTask task)
    {
        return task == BuilderTask.GE_BUY || task == BuilderTask.GE_SELL;
    }

    private void stopCurrentTaskScript()
    {
        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            tutorialIslandScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.COOKS_ASSISTANT)
        {
            cooksScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.GOBLIN_DIPLOMACY)
        {
            gobScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.MINING)
        {
            miningScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.WOODCUTTING)
        {
            woodCuttingScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.FIREMAKING)
        {
            fireMakingScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.FISHING)
        {
            fishingScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.MELEE)
        {
            meleeScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.GE_SELL || currentTask == BuilderTask.GE_BUY)
        {
            buyScript.shutdown();
            sellScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.SMITHING)
        {
            smithScript.shutdown();
            return;
        }

        smeltScript.shutdown();
        tutorialIslandScript.shutdown();
    }

    private boolean switchTask(BuilderTask nextTask)
    {
        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to switch task; still preparing bank/gear handoff to {}", nextTask);
            return false;
        }

        taskStarted = false;
        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            tutorialIslandScript.shutdown();
        }
        else if (currentTask == BuilderTask.COOKS_ASSISTANT)
        {
            cooksScript.shutdown();
        }
        else if (currentTask == BuilderTask.GOBLIN_DIPLOMACY)
        {
            gobScript.shutdown();
        }
        else if (currentTask == BuilderTask.MINING)
        {
            miningScript.shutdown();
        }
        else if (currentTask == BuilderTask.WOODCUTTING)
        {
            woodCuttingScript.shutdown();
        }
        else if (currentTask == BuilderTask.FIREMAKING)
        {
            fireMakingScript.shutdown();
        }
        else if (currentTask == BuilderTask.FISHING)
        {
            fishingScript.shutdown();
        }
        else if (currentTask == BuilderTask.MELEE)
        {
            meleeScript.shutdown();
        }
        else if (currentTask == BuilderTask.GE_SELL || currentTask == BuilderTask.GE_BUY)
        {
            buyScript.shutdown();
            sellScript.shutdown();
        }
        else if (currentTask == BuilderTask.SMITHING)
        {
            smithScript.shutdown();
        }
        else
        {
            smeltScript.shutdown();
        }

        currentTask = nextTask;
        debug("Switching task to {}", currentTask);
        return true;
    }

    private BuilderTask getRandomTaskExcluding(BuilderTask excludedTask)
    {
        if (TutorialIslandScript.isOnTutorialIsland())
        {
            return BuilderTask.TUTORIAL_ISLAND;
        }

        BuilderTask nextTask = getRandomPrimaryTaskExcluding(excludedTask);
        if (nextTask != null)
        {
            return nextTask;
        }

        BuilderTask[] tasks = BuilderTask.values();
        nextTask = tasks[ThreadLocalRandom.current().nextInt(tasks.length)];

        int safety = 0;
        while ((nextTask == excludedTask || nextTask == BuilderTask.TUTORIAL_ISLAND) && safety < 20)
        {
            nextTask = tasks[ThreadLocalRandom.current().nextInt(tasks.length)];
            safety++;
        }

        if (nextTask == BuilderTask.TUTORIAL_ISLAND)
        {
            return BuilderTask.MINING;
        }

        return nextTask;
    }

    private BuilderTask getRandomPrimaryTaskExcluding(BuilderTask excludedTask)
    {
        BuilderTask[] candidates = new BuilderTask[BuilderTask.values().length];
        int candidateCount = 0;

        for (BuilderTask task : BuilderTask.values())
        {
            if (task == excludedTask || task == BuilderTask.TUTORIAL_ISLAND || isSupportTask(task))
            {
                continue;
            }

            candidates[candidateCount++] = task;
        }

        if (candidateCount == 0)
        {
            return null;
        }

        return candidates[ThreadLocalRandom.current().nextInt(candidateCount)];
    }

    private boolean ensureInventoryTabOpenForTaskSelection()
    {
        if (!Microbot.isLoggedIn())
        {
            return false;
        }

        if (Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY)
        {
            return true;
        }

        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 1_500);

        return Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY;
    }

    private boolean prepareForTaskSwitchAtBank()
    {
        if (postTutorialBankCameraPending)
        {
            postTutorialBankCameraPending = !setPostTutorialBankCamera();
        }

        if (!ensureTaskSwitchBankOpen())
        {
            return false;
        }

        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        sleep(300);

        if (!depositInventoryForTaskSwitch())
        {
            return false;
        }

        if (!unequipGatheringTools())
        {
            return false;
        }

        if (!ensureTaskSwitchBankOpen())
        {
            return false;
        }

        depositGatheringToolsInInventory();

        return depositInventoryForTaskSwitch();
    }

    private boolean ensureTaskSwitchBankOpen()
    {
        if (Rs2Bank.isOpen())
        {
            return true;
        }

        if (tryOpenTaskSwitchBank("direct-open"))
        {
            sleepUntil(Rs2Bank::isOpen, 2_000);
            if (Rs2Bank.isOpen())
            {
                return true;
            }
        }

        if (tryWalkToTaskSwitchBank())
        {
            sleepUntil(Rs2Bank::isOpen, 3_000);
            return Rs2Bank.isOpen();
        }

        return Rs2Bank.isOpen();
    }

    private boolean tryOpenTaskSwitchBank(String source)
    {
        try
        {
            return Rs2Bank.openBank();
        }
        catch (RuntimeException ex)
        {
            debug("Task switch bank open failed | source={} message={}", source, ex.getMessage());
            return false;
        }
    }

    private boolean tryWalkToTaskSwitchBank()
    {
        try
        {
            return Rs2Bank.walkToBankAndUseBank();
        }
        catch (RuntimeException ex)
        {
            debug("Task switch bank walk failed | message={}", ex.getMessage());
            return false;
        }
    }

    private boolean setPostTutorialBankCamera()
    {
        if (isDialogueOpen())
        {
            debug("Waiting to set post-tutorial camera; dialogue is still open");
            return false;
        }

        Microbot.getClientThread().invoke(() ->
        {
            Microbot.getClient().setCameraPitchRelaxerEnabled(true);
            Microbot.getClient().setCameraPitchTarget(POST_TUTORIAL_BANK_CAMERA_PITCH);
            Microbot.getClient().setCameraYawTarget(POST_TUTORIAL_BANK_CAMERA_YAW);
        });
        Rs2Camera.setPitchInstant(POST_TUTORIAL_BANK_CAMERA_PITCH);
        Rs2Camera.setYawInstant(POST_TUTORIAL_BANK_CAMERA_YAW);
        boolean zoomSet = setPostTutorialZoomFromSettingsSlider();
        ensureInventoryTabOpenForTaskSelection();
        debug(
                "Set post-tutorial bank camera | targetPitch={} actualPitch={} targetYaw={} actualYaw={} targetScale={} actualZoom={}",
                POST_TUTORIAL_BANK_CAMERA_PITCH,
                Rs2Camera.getPitch(),
                POST_TUTORIAL_BANK_CAMERA_YAW,
                Rs2Camera.getYaw(),
                POST_TUTORIAL_BANK_CAMERA_SCALE,
                getZoom()
        );
        return zoomSet || isPostTutorialBankCameraSet();
    }

    private boolean setPostTutorialZoomFromSettingsSlider()
    {
        if (isDialogueOpen())
        {
            return false;
        }

        int target = POST_TUTORIAL_BANK_CAMERA_SCALE;

        if (Rs2Tab.getCurrentTab() != InterfaceTab.SETTINGS)
        {
            Rs2Tab.switchTo(InterfaceTab.SETTINGS);
            if (!sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.SETTINGS, 1500))
                return false;
        }

        if (Rs2Widget.isWidgetVisible(SETTINGS_SIDE_INTERFACE_ID, SETTINGS_ZOOM_3_WIDGET_ID))
        {
            Rs2Widget.clickChildWidget(SETTINGS_SIDE_INTERFACE_ID, SETTINGS_ZOOM_3_WIDGET_ID);
            if (sleepUntil(() -> Math.abs(getZoom() - target) <= CAMERA_ZOOM_TOLERANCE, 1_500))
            {
                return true;
            }
        }

        Microbot.getClientThread().invoke(() ->
        {
            Microbot.getClient().setVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT, target);
            Microbot.getClient().setVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT, target);
            Microbot.getClient().runScript(ScriptID.CAMERA_DO_ZOOM, target, target);
        });

        return sleepUntil(() -> Math.abs(getZoom() - target) <= CAMERA_ZOOM_TOLERANCE, 1500);
    }

    private boolean isDialogueOpen()
    {
        try
        {
            return Rs2Dialogue.isInDialogue() || Rs2Dialogue.hasContinue();
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private int getZoom()
    {
        return Microbot.getClient().isResized()
                ? Microbot.getClient().getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT)
                : Microbot.getClient().getVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT);
    }


    private boolean isPostTutorialBankCameraSet()
    {
        return Math.abs(Rs2Camera.getPitch() - POST_TUTORIAL_BANK_CAMERA_PITCH) <= 8
                && Math.abs(Rs2Camera.getYaw() - POST_TUTORIAL_BANK_CAMERA_YAW) <= 8
                && Math.abs(getZoom() - POST_TUTORIAL_BANK_CAMERA_SCALE) <= CAMERA_ZOOM_TOLERANCE;
    }

    private boolean depositInventoryForTaskSwitch()
    {
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        if (closeTaskSwitchBankTutorialOverlayIfOpen())
        {
            return false;
        }

        if (!Rs2Inventory.isEmpty())
        {
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 1_500);
            sleep(200);
        }

        return Rs2Bank.isOpen();
    }

    private boolean unequipGatheringTools()
    {
        boolean hasPickaxeEquipped = Rs2Equipment.isWearing("pickaxe", false) || Rs2Equipment.isWearing("pickaxe");
        boolean hasAxeEquipped = Rs2Equipment.isWearing("axe", false) || Rs2Equipment.isWearing("axe");
        if (!hasPickaxeEquipped && !hasAxeEquipped)
        {
            return true;
        }

        if (Rs2Bank.isOpen())
        {
            if (closeTaskSwitchBankTutorialOverlayIfOpen())
            {
                return false;
            }

            Rs2Bank.depositEquipment();
            sleepUntil(() -> !isGatheringToolEquipped(), 1_500);
            return !isGatheringToolEquipped();
        }

        return false;
    }

    private boolean isGatheringToolEquipped()
    {
        return Rs2Equipment.isWearing("pickaxe", false) || Rs2Equipment.isWearing("pickaxe")
                || Rs2Equipment.isWearing("axe", false) || Rs2Equipment.isWearing("axe");
    }

    private void depositGatheringToolsInInventory()
    {
        if (closeTaskSwitchBankTutorialOverlayIfOpen())
        {
            return;
        }

        for (String pickaxeName : Buy.PICKAXE_NAMES)
        {
            if (Rs2Inventory.hasItem(pickaxeName))
            {
                Rs2Bank.depositAll(pickaxeName);
                sleep(100);
            }
        }

        for (String axeName : Buy.AXE_NAMES)
        {
            if (Rs2Inventory.hasItem(axeName))
            {
                Rs2Bank.depositAll(axeName);
                sleep(100);
            }
        }
    }

    private boolean closeTaskSwitchBankTutorialOverlayIfOpen()
    {
        if (!KspBankWidgetHelper.closeBankTutorialOverlayIfOpen())
        {
            return false;
        }

        sleep(300);
        if (Rs2Bank.isOpen())
        {
            debug("Closed bank tutorial overlay during task switch; resetting bank");
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
        }

        return true;
    }

    private void scheduleNextBreak()
    {
        if (!config.doBreaks())
        {
            nextBreakAtMillis = -1L;
            breakEndsAtMillis = -1L;
            return;
        }

        long delayMinutes = randomMinutes(config.breakAfterMinMinutes(), config.breakAfterMaxMinutes());
        nextBreakAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes);
    }

    private void scheduleNextActivitySwitch()
    {
        if (!canUseActivitySwitchTimer())
        {
            nextActivitySwitchAtMillis = -1L;
            return;
        }

        long delayMinutes = randomMinutes(config.activitySwitchMinMinutes(), config.activitySwitchMaxMinutes());
        nextActivitySwitchAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes);
    }

    private void maybeStartActivitySwitchTimer()
    {
        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            clearActivitySwitchTimerState();
            return;
        }

        if (!canUseActivitySwitchTimer() || !awaitingActivitySwitchTimerStart || !taskStarted)
        {
            return;
        }

        if (!isInCurrentTaskArea())
        {
            return;
        }

        awaitingActivitySwitchTimerStart = false;
        scheduleNextActivitySwitch();
        debug("Started activity switch timer for {} after reaching task area", currentTask);
    }

    private boolean isInCurrentTaskArea()
    {
        if (Rs2Player.getWorldLocation() == null)
        {
            return false;
        }

        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            return false;
        }

        if (currentTask == BuilderTask.MINING)
        {
            return miningScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.WOODCUTTING)
        {
            return woodCuttingScript.getTargetArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.FIREMAKING)
        {
            return fireMakingScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.FISHING)
        {
            return fishingScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.MELEE)
        {
            return meleeScript.getTargetArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.GE_SELL)
        {
            return sellScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.GE_BUY)
        {
            return buyScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.SMITHING)
        {
            return smithScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        return smeltScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
    }

    private int randomMinutes(int min, int max)
    {
        int safeMin = Math.min(min, max);
        int safeMax = Math.max(min, max);
        return ThreadLocalRandom.current().nextInt(safeMin, safeMax + 1);
    }


    private void applyAntibanSettings()
    {
        Rs2Antiban.resetAntibanSettings();

        if (!config.useAntiban())
        {
            return;
        }

        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        for (PlayStyle playStyle : PlayStyle.values())
        {
            playStyle.resetPlayStyle();
        }
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.universalAntiban = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.nonLinearIntervals = false;
        Rs2AntibanSettings.actionCooldownChance = 0.20;
    }

    private void debug(String message, Object... args)
    {
        if (debugEnabled)
        {
            KspTaskDebug.info(log, true, "Builder", message, args);
        }
    }

    private void maybeLogStatus()
    {
        long now = System.currentTimeMillis();
        if (now - lastStatusLogAt >= 10_000)
        {
            WorldPoint playerLocation = getSafePlayerLocation();
            debug("active task | currentTask={} pendingTask={} breakActive={} player={} moving={} animating={} interacting={} bankOpen={} taskStarted={}",
                    currentTask,
                    pendingTask,
                    breakActive,
                    playerLocation,
                    playerLocation != null && Rs2Player.isMoving(),
                    playerLocation != null && Rs2Player.isAnimating(),
                    playerLocation != null && Rs2Player.isInteracting(),
                    Rs2Bank.isOpen(),
                    taskStarted);
            lastStatusLogAt = now;
        }
    }

    private WorldPoint getSafePlayerLocation()
    {
        try
        {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null)
            {
                return null;
            }

            return Rs2Player.getWorldLocation();
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private void captureOriginalWindowTitle()
    {
        try
        {
            String currentTitle = ClientUI.getFrame().getTitle();
            if (currentTitle != null && !currentTitle.isEmpty())
            {
                originalWindowTitle = currentTitle;
            }
        }
        catch (Exception ignored)
        {
        }
    }

    private void updateWindowTitle()
    {
        try
        {
            if (!breakActive)
            {
                ClientUI.getFrame().setTitle(originalWindowTitle);
                return;
            }

            long breakRemaining = getBreakTimeRemainingSeconds();
            String title = originalWindowTitle + " - Breaking for: " + formatBreakDuration(breakRemaining);
            ClientUI.getFrame().setTitle(title);
        }
        catch (Exception ignored)
        {
        }
    }

    private String formatBreakDuration(long totalSeconds)
    {
        if (totalSeconds < 0)
        {
            return "--:--";
        }

        Duration duration = Duration.ofSeconds(totalSeconds);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (hours > 0)
        {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }

        return String.format("%02d:%02d", minutes, seconds);
    }

    private void attemptLoginAfterBreak()
    {
        long now = System.currentTimeMillis();
        if ((now - lastBreakLoginAttemptAt) < TimeUnit.SECONDS.toMillis(10))
        {
            return;
        }

        lastBreakLoginAttemptAt = now;
        stopExternalAutoLoginPlugin("post-break-login");

        if (LoginManager.getActiveProfile() == null)
        {
            debug("Unable to log back in after break; no active login profile is configured");
            return;
        }

        try
        {
            int world = LoginManager.getRandomWorld(false);
            boolean loginStarted = LoginManager.login(world);
            debug("Triggered manual F2P login after break | world={} started={}", world, loginStarted);
        }
        catch (Exception ex)
        {
            debug("Failed to trigger login after break: {}", ex.getMessage());
        }
    }

    private void stopExternalAutoLoginPlugin(String reason)
    {
        try
        {
            var externalAutoLoginPlugin = Microbot.getPlugin(EXTERNAL_AUTO_LOGIN_PLUGIN_CLASS);
            if (externalAutoLoginPlugin == null || !Microbot.isPluginEnabled(externalAutoLoginPlugin))
            {
                return;
            }

            boolean stopped = Microbot.stopPlugin(externalAutoLoginPlugin);
            debug("Stopped standalone AutoLogin plugin | reason={} stopped={}", reason, stopped);
        }
        catch (Exception ex)
        {
            debug("Failed to stop standalone AutoLogin plugin | reason={} error={}", reason, ex.getMessage());
        }
    }

    public String getCurrentTaskName()
    {
        BuilderTask pending = pendingTask;
        if (pending != null)
        {
            return "SWITCHING_TO_" + pending.name();
        }

        BuilderTask current = currentTask;
        return current == null ? "IDLE" : current.name();
    }

    public long getRuntimeSeconds()
    {
        if (startedAtMillis <= 0L)
        {
            return 0L;
        }
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startedAtMillis);
    }

    public long getTimeUntilBreakSeconds()
    {
        if (!config.doBreaks() || breakActive || nextBreakAtMillis <= 0L)
        {
            return -1L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(nextBreakAtMillis - System.currentTimeMillis()));
    }

    public long getBreakTimeRemainingSeconds()
    {
        if (!breakActive || breakEndsAtMillis <= 0L)
        {
            return -1L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(breakEndsAtMillis - System.currentTimeMillis()));
    }

    public long getTimeUntilActivitySwitchSeconds()
    {
        if (isSingleSkillTaskForced())
        {
            return -1L;
        }

        if (!canUseActivitySwitchTimer()
                || nextActivitySwitchAtMillis <= 0L
                || pendingTask != null
                || pendingRandomTaskSelection
                || awaitingNextActivityStart)
        {
            return -1L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(nextActivitySwitchAtMillis - System.currentTimeMillis()));
    }

    @Override
    public void shutdown()
    {
        shuttingDown = true;
        super.shutdown();
        taskStarted = false;
        breakActive = false;
        startedAtMillis = 0L;
        nextBreakAtMillis = -1L;
        breakEndsAtMillis = -1L;
        nextActivitySwitchAtMillis = -1L;
        debugEnabled = false;
        pendingTask = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = false;
        breakLogoutRequested = false;
        postTutorialBankCameraPending = false;
        lastBreakLoginAttemptAt = 0L;
        updateWindowTitle();

        if (miningScript != null)
        {
            miningScript.shutdown();
        }

        if (woodCuttingScript != null)
        {
            woodCuttingScript.shutdown();
        }

        if (fireMakingScript != null)
        {
            fireMakingScript.shutdown();
        }

        if (fishingScript != null)
        {
            fishingScript.shutdown();
        }

        if (meleeScript != null)
        {
            meleeScript.shutdown();
        }

        if (buyScript != null)
        {
            buyScript.shutdown();
        }

        if (sellScript != null)
        {
            sellScript.shutdown();
        }

        if (smithScript != null)
        {
            smithScript.shutdown();
        }

        if (smeltScript != null)
        {
            smeltScript.shutdown();
        }

        if (tutorialIslandScript != null)
        {
            tutorialIslandScript.shutdown();
        }

        if (cooksScript != null)
        {
            cooksScript.shutdown();
        }

        if (gobScript != null)
        {
            gobScript.shutdown();
        }

        if (autoLoginScript != null)
        {
            autoLoginScript.shutdown();
        }

        if (experienceLampScript != null)
        {
            experienceLampScript.shutdown();
        }

        Rs2Antiban.resetAntibanSettings();
    }
}
