package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript.MiningScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea.SmeltArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltscript.SmeltScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas.TreeAreas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.woodcuttingscript.WoodCuttingScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class KspAccountBuilderScript extends Script
{
    private static final String[] PICKAXE_NAMES = {
        "Bronze pickaxe", "Iron pickaxe", "Steel pickaxe", "Black pickaxe",
        "Mithril pickaxe", "Adamant pickaxe", "Rune pickaxe"
    };
    private static final String[] AXE_NAMES = {
        "Bronze axe", "Iron axe", "Steel axe", "Black axe",
        "Mithril axe", "Adamant axe", "Rune axe"
    };

    private enum BuilderTask
    {
        MINING,
        WOODCUTTING,
        SMELTING
    }

    private static final int LOOP_DELAY_MS = 600;

    @Inject
    private MiningScript miningScript;

    @Inject
    private WoodCuttingScript woodCuttingScript;

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

    public boolean run(KspAccountBuilderConfig config)
    {
        shutdown();
        this.config = config;
        this.debugEnabled = config.debugLogging();
        miningScript.setDebugLogging(debugEnabled);
        woodCuttingScript.setDebugLogging(debugEnabled);
        smeltScript.setDebugLogging(debugEnabled);
        applyAntibanSettings();

        currentTask = getRandomTaskExcluding(null);
        taskStarted = false;
        breakActive = false;
        pendingTask = null;
        awaitingNextActivityStart = false;

        startedAtMillis = System.currentTimeMillis();
        lastStatusLogAt = 0L;

        scheduleNextBreak();
        scheduleNextActivitySwitch();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            debugEnabled = config.debugLogging();
            miningScript.setDebugLogging(debugEnabled);
            woodCuttingScript.setDebugLogging(debugEnabled);
            smeltScript.setDebugLogging(debugEnabled);

            processTimers();
            if (breakActive || pendingTask != null)
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

        if (config.doBreaks())
        {
            if (!breakActive && now >= nextBreakAtMillis)
            {
                breakActive = true;
                taskStarted = false;
                pendingTask = null;
                awaitingNextActivityStart = false;
                miningScript.shutdown();
                woodCuttingScript.shutdown();
                smeltScript.shutdown();
                breakEndsAtMillis = now + TimeUnit.MINUTES.toMillis(randomMinutes(config.breakDurationMinMinutes(), config.breakDurationMaxMinutes()));
                debug("Starting break for {} seconds", getBreakTimeRemainingSeconds());
            }

            if (breakActive && now >= breakEndsAtMillis)
            {
                breakActive = false;
                scheduleNextBreak();
                debug("Break completed, resuming tasks");
            }
        }

        if (!config.enableActivitySwitchRandomization() || awaitingNextActivityStart)
        {
            return;
        }

        if (pendingTask == null && now >= nextActivitySwitchAtMillis)
        {
            pendingTask = getRandomTaskExcluding(currentTask);
            taskStarted = false;
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            smeltScript.shutdown();
            debug("Preparing activity switch: {} -> {}", currentTask, pendingTask);
        }

        if (pendingTask != null && switchTask(pendingTask))
        {
            pendingTask = null;
            awaitingNextActivityStart = true;
        }
    }

    private void runAccountBuilderCycle()
    {
        if (taskStarted)
        {
            return;
        }

        if (currentTask == BuilderTask.MINING)
        {
            woodCuttingScript.shutdown();
            smeltScript.shutdown();
            taskStarted = miningScript.run(Areas.TIN_COPPER_VARROCK_EAST);
        }
        else if (currentTask == BuilderTask.WOODCUTTING)
        {
            miningScript.shutdown();
            smeltScript.shutdown();
            taskStarted = woodCuttingScript.run(TreeAreas.REGULAR_TREE_VARROCK_WEST);
        }
        else
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            taskStarted = smeltScript.run(SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE, BarLevels.BRONZE);
        }

        if (taskStarted && awaitingNextActivityStart)
        {
            awaitingNextActivityStart = false;
            scheduleNextActivitySwitch();
            debug("Activity switch completed; next switch timer rescheduled");
        }
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

        if (miningScript != null)
        {
            miningScript.shutdown();
        }

        if (woodCuttingScript != null)
        {
            woodCuttingScript.shutdown();
        }

        if (smeltScript != null)
        {
            smeltScript.shutdown();
        }

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
