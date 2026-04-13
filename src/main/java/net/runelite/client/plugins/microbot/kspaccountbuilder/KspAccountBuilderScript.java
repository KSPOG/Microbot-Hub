package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.firemakingscript.FireMakingScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.fmarea.FireArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.loglevels.LogsLvl;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript.MiningScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.BuyScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell.SellList;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript.SellScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smitharea.SmithArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithlevels.SmithLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithscript.SmithScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.tool.SmithTool;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.oresreq.ReqOres;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea.SmeltArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltscript.SmeltScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas.TreeAreas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.woodcuttingscript.WoodCuttingScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
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
    private static final String DEVELOPER_PASSWORD = "KSPUltra";
    private static final String[] PICKAXE_NAMES = {
            "Bronze pickaxe", "Iron pickaxe", "Steel pickaxe", "Black pickaxe",
            "Mithril pickaxe", "Adamant pickaxe", "Rune pickaxe"
    };
    private static final String[] AXE_NAMES = {
            "Bronze axe", "Iron axe", "Steel axe", "Black axe",
            "Mithril axe", "Adamant axe", "Rune axe"
    };
    private static final int[] SAFE_F2P_LOGIN_WORLDS = {301, 308, 316, 326, 335};

    private enum BuilderTask
    {
        MINING,
        WOODCUTTING,
        FIREMAKING,
        GE_SELL,
        GE_BUY,
        SMITHING,
        SMELTING
    }

    private static final int LOOP_DELAY_MS = 600;

    @Inject
    private MiningScript miningScript;

    @Inject
    private WoodCuttingScript woodCuttingScript;

    @Inject
    private FireMakingScript fireMakingScript;

    @Inject
    private BuyScript buyScript;

    @Inject
    private SellScript sellScript;

    @Inject
    private SmithScript smithScript;

    @Inject
    private SmeltScript smeltScript;

    @Getter
    private BuilderTask currentTask = getRandomTaskExcluding(null);

    @Getter
    private boolean breakActive;

    @Getter
    private long startedAtMillis;

    private boolean taskStarted;
    private long nextBreakAtMillis;
    private long breakEndsAtMillis;
    private long nextActivitySwitchAtMillis;
    private long lastStatusLogAt;
    private KspAccountBuilderConfig config;
    private boolean debugEnabled;
    private BuilderTask pendingTask;
    private boolean awaitingNextActivityStart;
    private boolean awaitingActivitySwitchTimerStart;
    private boolean breakLogoutRequested;
    private long lastBreakLoginAttemptAt;
    private String originalWindowTitle = "Microbot";

    public boolean run(KspAccountBuilderConfig config)
    {
        shutdown();
        this.config = config;
        this.debugEnabled = config.debugLogging();
        miningScript.setDebugLogging(debugEnabled);
        woodCuttingScript.setDebugLogging(debugEnabled);
        fireMakingScript.setDebugLogging(debugEnabled);
        buyScript.setDebugLogging(debugEnabled);
        sellScript.setDebugLogging(debugEnabled);
        smithScript.setDebugLogging(debugEnabled);
        smeltScript.setDebugLogging(debugEnabled);
        applyAntibanSettings();

        currentTask = getRandomTaskExcluding(null);
        taskStarted = false;
        breakActive = false;
        pendingTask = null;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = config.enableActivitySwitchRandomization();
        breakLogoutRequested = false;
        lastBreakLoginAttemptAt = 0L;
        captureOriginalWindowTitle();

        startedAtMillis = System.currentTimeMillis();
        lastStatusLogAt = 0L;

        scheduleNextBreak();
        nextActivitySwitchAtMillis = -1L;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run())
            {
                return;
            }

            debugEnabled = config.debugLogging();
            miningScript.setDebugLogging(debugEnabled);
            woodCuttingScript.setDebugLogging(debugEnabled);
            fireMakingScript.setDebugLogging(debugEnabled);
            buyScript.setDebugLogging(debugEnabled);
            sellScript.setDebugLogging(debugEnabled);
            smithScript.setDebugLogging(debugEnabled);
            smeltScript.setDebugLogging(debugEnabled);

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

            runAccountBuilderCycle();
            maybeLogStatus();
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void processTimers()
    {
        long now = System.currentTimeMillis();
        boolean developerTaskForced = isDeveloperTaskForced();

        if (config.doBreaks())
        {
            if (!breakActive && now >= nextBreakAtMillis)
            {
                breakActive = true;
                taskStarted = false;
                pendingTask = null;
                awaitingNextActivityStart = false;
                breakLogoutRequested = false;
                lastBreakLoginAttemptAt = 0L;
                miningScript.shutdown();
                woodCuttingScript.shutdown();
                fireMakingScript.shutdown();
                buyScript.shutdown();
                sellScript.shutdown();
                smithScript.shutdown();
                smeltScript.shutdown();
                breakEndsAtMillis = now + TimeUnit.MINUTES.toMillis(randomMinutes(config.breakDurationMinMinutes(), config.breakDurationMaxMinutes()));
                debug("Starting break for {} seconds", getBreakTimeRemainingSeconds());
            }

            if (breakActive && Microbot.isLoggedIn() && !breakLogoutRequested)
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

                if (!Microbot.isLoggedIn())
                {
                    attemptLoginAfterBreak();
                }
            }
        }

        if (developerTaskForced)
        {
            pendingTask = null;
            awaitingNextActivityStart = false;
            awaitingActivitySwitchTimerStart = false;
            nextActivitySwitchAtMillis = -1L;
            return;
        }

        if (!config.enableActivitySwitchRandomization() || awaitingNextActivityStart || awaitingActivitySwitchTimerStart)
        {
            return;
        }

        if (pendingTask == null && now >= nextActivitySwitchAtMillis)
        {
            pendingTask = getRandomTaskExcluding(currentTask);
            taskStarted = false;
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            debug("Preparing activity switch: {} -> {}", currentTask, pendingTask);
        }

        if (pendingTask != null && switchTask(pendingTask))
        {
            pendingTask = null;
            awaitingNextActivityStart = true;
            awaitingActivitySwitchTimerStart = true;
            nextActivitySwitchAtMillis = -1L;
        }
    }

    private void runAccountBuilderCycle()
    {
        applyDeveloperOverrides();

        if (!isDeveloperTaskForced() && !hasResourcesForTask(currentTask))
        {
            if (!switchToTaskWithResources())
            {
                stopCurrentTaskScript();
                taskStarted = false;
                return;
            }
        }

        if (taskStarted)
        {
            maybeStartActivitySwitchTimer();
            return;
        }

        if (currentTask == BuilderTask.MINING)
        {
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            taskStarted = miningScript.run(resolveMiningStartArea());
        }
        else if (currentTask == BuilderTask.WOODCUTTING)
        {
            miningScript.shutdown();
            fireMakingScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            taskStarted = woodCuttingScript.run(resolveWoodcuttingStartArea());
        }
        else if (currentTask == BuilderTask.FIREMAKING)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            taskStarted = fireMakingScript.run(FireArea.FM_AREA_DRAYNOR_BANK);
        }
        else if (currentTask == BuilderTask.GE_SELL)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            buyScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            taskStarted = sellScript.run(GEArea.GRAND_EXCHANGE);
        }
        else if (currentTask == BuilderTask.GE_BUY)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            taskStarted = buyScript.run(GEArea.GRAND_EXCHANGE);
        }
        else if (currentTask == BuilderTask.SMITHING)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smeltScript.shutdown();
            taskStarted = smithScript.run(SmithArea.SMITH_AREA_VARROCK_WEST_ANVIL);
        }
        else
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            taskStarted = smeltScript.run(SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE, resolveSmeltingFallbackBar());
        }

        if (taskStarted && awaitingNextActivityStart)
        {
            awaitingNextActivityStart = false;
            debug("Activity switch completed; waiting to reach task area before starting next switch timer");
        }

        maybeStartActivitySwitchTimer();
    }

    private void applyDeveloperOverrides()
    {
        if (!isDeveloperModeUnlocked())
        {
            return;
        }

        BuilderTask developerTask = resolveDeveloperTask();
        if (developerTask == null || currentTask == developerTask)
        {
            return;
        }

        stopCurrentTaskScript();
        taskStarted = false;
        pendingTask = null;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = config.enableActivitySwitchRandomization();
        nextActivitySwitchAtMillis = -1L;
        currentTask = developerTask;
        debug("Developer override switched task to {}", currentTask);
    }

    private boolean isDeveloperModeUnlocked()
    {
        String configuredPassword = config.developerPassword();
        return configuredPassword != null && configuredPassword.equals(DEVELOPER_PASSWORD);
    }

    private BuilderTask resolveDeveloperTask()
    {
        if (!isDeveloperModeUnlocked())
        {
            return null;
        }

        if (config.developerTask() == KspDeveloperTask.MINING)
        {
            return BuilderTask.MINING;
        }

        if (config.developerTask() == KspDeveloperTask.WOODCUTTING)
        {
            return BuilderTask.WOODCUTTING;
        }

        if (config.developerTask() == KspDeveloperTask.GE_SELL)
        {
            return BuilderTask.GE_SELL;
        }

        if (config.developerTask() == KspDeveloperTask.GE_BUY)
        {
            return BuilderTask.GE_BUY;
        }

        if (config.developerTask() == KspDeveloperTask.SMELTING)
        {
            return BuilderTask.SMELTING;
        }

        if (config.developerTask() == KspDeveloperTask.SMITHING)
        {
            return BuilderTask.SMITHING;
        }

        return null;
    }

    private boolean isDeveloperTaskForced()
    {
        return resolveDeveloperTask() != null;
    }

    private Areas resolveMiningStartArea()
    {
        if (!isDeveloperModeUnlocked())
        {
            return Areas.TIN_COPPER_VARROCK_EAST;
        }

        KspDeveloperMiningTarget developerMiningTarget = config.developerMiningTarget();
        return developerMiningTarget.getArea() != null ? developerMiningTarget.getArea() : Areas.TIN_COPPER_VARROCK_EAST;
    }

    private TreeAreas resolveWoodcuttingStartArea()
    {
        if (!isDeveloperModeUnlocked())
        {
            return TreeAreas.REGULAR_TREE_VARROCK_WEST;
        }

        KspDeveloperWoodcuttingTarget developerWoodcuttingTarget = config.developerWoodcuttingTarget();
        return developerWoodcuttingTarget.getTreeArea() != null ? developerWoodcuttingTarget.getTreeArea() : TreeAreas.REGULAR_TREE_VARROCK_WEST;
    }

    private BarLevels resolveSmeltingFallbackBar()
    {
        if (!isDeveloperModeUnlocked())
        {
            return BarLevels.BRONZE;
        }

        KspDeveloperSmeltingTarget developerSmeltingTarget = config.developerSmeltingTarget();
        return developerSmeltingTarget.getBarLevel() != null ? developerSmeltingTarget.getBarLevel() : BarLevels.BRONZE;
    }

    private boolean hasResourcesForTask(BuilderTask task)
    {
        if (task == BuilderTask.MINING)
        {
            return hasAnyToolAvailable(PICKAXE_NAMES);
        }

        if (task == BuilderTask.WOODCUTTING)
        {
            return hasAnyToolAvailable(AXE_NAMES);
        }

        if (task == BuilderTask.FIREMAKING)
        {
            return hasAnyFiremakingResourcesAvailable();
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
        boolean hasTinderboxAvailable = Rs2Inventory.hasItem("Tinderbox") || Rs2Bank.count("Tinderbox") > 0;
        if (!hasTinderboxAvailable)
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

    private boolean hasAnySmithingResourcesAvailable()
    {
        String hammerName = SmithTool.HAMMER.getDisplayName();
        boolean hasHammerAvailable = Rs2Inventory.hasItem(hammerName) || Rs2Bank.count(hammerName) > 0;
        if (!hasHammerAvailable)
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
        return isToolUpgradeMissingAnywhere();
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

    private boolean isToolUpgradeMissingAnywhere()
    {
        String desiredPickaxe = resolveDesiredPickaxeForSellingTask();
        String desiredAxe = resolveDesiredAxeForSellingTask();

        return !hasToolAnywhere(desiredPickaxe) || !hasToolAnywhere(desiredAxe);
    }

    private boolean hasAnyOutdatedToolAvailable()
    {
        String desiredPickaxe = resolveDesiredPickaxeForSellingTask();
        String desiredAxe = resolveDesiredAxeForSellingTask();

        for (String pickaxeName : PICKAXE_NAMES)
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

        for (String axeName : AXE_NAMES)
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
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        return PICKAXE_NAMES[Math.min(
                net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.levelreqmining.MiningReq.bestForMiningLevel(miningLevel).ordinal(),
                net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.equiplevels.PickaxeEquip.bestForAttackLevel(attackLevel).ordinal()
        )];
    }

    private String resolveDesiredAxeForSellingTask()
    {
        int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        return AXE_NAMES[Math.min(
                net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.levelreqwc.WoodCuttingReq.bestForWoodcuttingLevel(woodcuttingLevel).ordinal(),
                net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.equiplevels.AxeEquip.bestForAttackLevel(attackLevel).ordinal()
        )];
    }

    private boolean hasToolAnywhere(String toolName)
    {
        return Rs2Equipment.isWearing(toolName)
                || Rs2Inventory.hasItem(toolName)
                || Rs2Inventory.hasItem(toolName, true)
                || Rs2Bank.count(toolName) > 0;
    }

    private boolean switchToTaskWithResources()
    {
        BuilderTask nextTask = getRandomTaskWithResourcesExcluding(currentTask);
        if (nextTask == null)
        {
            debug("Current task {} has no resources and no alternative task is available", currentTask);
            return false;
        }

        stopCurrentTaskScript();
        taskStarted = false;
        pendingTask = null;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = config.enableActivitySwitchRandomization();
        nextActivitySwitchAtMillis = -1L;

        debug("Switching task from {} to {} because resources are unavailable", currentTask, nextTask);
        currentTask = nextTask;
        return true;
    }

    private BuilderTask getRandomTaskWithResourcesExcluding(BuilderTask excludedTask)
    {
        BuilderTask[] candidates = new BuilderTask[BuilderTask.values().length];
        int candidateCount = 0;

        for (BuilderTask task : BuilderTask.values())
        {
            if (task == excludedTask)
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

    private void stopCurrentTaskScript()
    {
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
    }

    private boolean switchTask(BuilderTask nextTask)
    {
        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to switch task; still preparing bank/gear handoff to {}", nextTask);
            return false;
        }

        taskStarted = false;
        if (currentTask == BuilderTask.MINING)
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
        BuilderTask[] tasks = BuilderTask.values();
        if (excludedTask == null)
        {
            return tasks[ThreadLocalRandom.current().nextInt(tasks.length)];
        }

        BuilderTask nextTask = tasks[ThreadLocalRandom.current().nextInt(tasks.length)];
        int safety = 0;
        while (nextTask == excludedTask && safety < 10)
        {
            nextTask = tasks[ThreadLocalRandom.current().nextInt(tasks.length)];
            safety++;
        }
        return nextTask;
    }

    private boolean prepareForTaskSwitchAtBank()
    {
        if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank())
        {
            return false;
        }

        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        sleep(300);

        if (!unequipGatheringTools())
        {
            return false;
        }

        depositGatheringToolsInInventory();

        if (!Rs2Inventory.isEmpty())
        {
            Rs2Bank.depositAll();
            sleep(350);
        }

        return true;
    }

    private boolean unequipGatheringTools()
    {
        boolean hasPickaxeEquipped = Rs2Equipment.isWearing("pickaxe", false) || Rs2Equipment.isWearing("pickaxe");
        boolean hasAxeEquipped = Rs2Equipment.isWearing("axe", false) || Rs2Equipment.isWearing("axe");
        if (!hasPickaxeEquipped && !hasAxeEquipped)
        {
            return true;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.EQUIPMENT)
        {
            Rs2Tab.switchTo(InterfaceTab.EQUIPMENT);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.EQUIPMENT, 1_500);
        }

        for (int attempt = 0; attempt < 3; attempt++)
        {
            if (!isGatheringToolEquipped())
            {
                break;
            }

            Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
            sleep(200);
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY)
        {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 1_500);
        }

        return !isGatheringToolEquipped();
    }

    private boolean isGatheringToolEquipped()
    {
        return Rs2Equipment.isWearing("pickaxe", false) || Rs2Equipment.isWearing("pickaxe")
                || Rs2Equipment.isWearing("axe", false) || Rs2Equipment.isWearing("axe");
    }

    private void depositGatheringToolsInInventory()
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (Rs2Inventory.hasItem(pickaxeName))
            {
                Rs2Bank.depositAll(pickaxeName);
                sleep(100);
            }
        }

        for (String axeName : AXE_NAMES)
        {
            if (Rs2Inventory.hasItem(axeName))
            {
                Rs2Bank.depositAll(axeName);
                sleep(100);
            }
        }
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
        if (!config.enableActivitySwitchRandomization())
        {
            nextActivitySwitchAtMillis = -1L;
            return;
        }

        long delayMinutes = randomMinutes(config.activitySwitchMinMinutes(), config.activitySwitchMaxMinutes());
        nextActivitySwitchAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes);
    }

    private void maybeStartActivitySwitchTimer()
    {
        if (!config.enableActivitySwitchRandomization() || !awaitingActivitySwitchTimerStart || !taskStarted)
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

        if (currentTask == BuilderTask.MINING)
        {
            return miningScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.WOODCUTTING)
        {
            return woodCuttingScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.FIREMAKING)
        {
            return fireMakingScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
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
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.universalAntiban = true;
        Rs2AntibanSettings.actionCooldownChance = 0.20;
    }

    private void debug(String message, Object... args)
    {
        if (debugEnabled)
        {
            log.debug(message, args);
        }
    }

    private void maybeLogStatus()
    {
        long now = System.currentTimeMillis();
        if (now - lastStatusLogAt >= 10_000)
        {
            debug("KSP Account Builder active task: {} | pendingTask={} | breakActive={}", currentTask, pendingTask, breakActive);
            lastStatusLogAt = now;
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

        if (attemptAutoLogin())
        {
            debug("Triggered AutoLogin after break");
            return;
        }

        if (LoginManager.getActiveProfile() == null)
        {
            debug("Unable to log back in after break; no active login profile is configured");
            return;
        }

        try
        {
            boolean isMemberProfile = LoginManager.getActiveProfile().isMember();
            int world = isMemberProfile
                    ? Login.getRandomWorld(true)
                    : SAFE_F2P_LOGIN_WORLDS[ThreadLocalRandom.current().nextInt(SAFE_F2P_LOGIN_WORLDS.length)];

            new Login(world);
            debug("Triggered manual login after break");
        }
        catch (Exception ex)
        {
            debug("Failed to trigger login after break: {}", ex.getMessage());
        }
    }

    private boolean attemptAutoLogin()
    {
        try
        {
            AutoLoginPlugin autoLoginPlugin = (AutoLoginPlugin) Microbot.getPlugin(AutoLoginPlugin.class.getName());
            if (autoLoginPlugin == null)
            {
                return false;
            }

            if (!Microbot.isPluginEnabled(autoLoginPlugin.getClass()))
            {
                Microbot.getClientThread().runOnSeperateThread(() ->
                {
                    Microbot.startPlugin(autoLoginPlugin);
                    return true;
                });
            }
            return true;
        }
        catch (Exception ex)
        {
            debug("Failed to trigger AutoLogin after break: {}", ex.getMessage());
            return false;
        }
    }

    public String getCurrentTaskName()
    {
        if (pendingTask != null)
        {
            return "SWITCHING_TO_" + pendingTask.name();
        }

        return currentTask.name();
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
        if (isDeveloperTaskForced())
        {
            return -1L;
        }

        if (!config.enableActivitySwitchRandomization() || nextActivitySwitchAtMillis <= 0L || pendingTask != null || awaitingNextActivityStart)
        {
            return -1L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(nextActivitySwitchAtMillis - System.currentTimeMillis()));
    }

    @Override
    public void shutdown()
    {
        taskStarted = false;
        breakActive = false;
        startedAtMillis = 0L;
        nextBreakAtMillis = -1L;
        breakEndsAtMillis = -1L;
        nextActivitySwitchAtMillis = -1L;
        debugEnabled = false;
        pendingTask = null;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = false;
        breakLogoutRequested = false;
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

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
