package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.woodcuttingscript;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.equiplevels.AxeEquip;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.levelreqwc.WoodCuttingReq;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas.TreeAreas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treelevel.TreeLevel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class WoodCuttingScript extends Script
{
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3_000;
    private static final int MID_TIER_RANDOM_MAX_LEVEL = 60;
    private static final List<String> AXE_NAMES = Arrays.asList(
            "Bronze axe",
            "Iron axe",
            "Steel axe",
            "Black axe",
            "Mithril axe",
            "Adamant axe",
            "Rune axe"
    );

    @Getter
    private TreeAreas targetArea = TreeAreas.REGULAR_TREE_VARROCK_WEST;

    private boolean startingTargetTreeInitialized;
    private TreeLevel randomMidTierTree;
    private boolean debugLogging;
    private long lastWebWalkAtMs;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public boolean run(TreeAreas area)
    {
        shutdown();
        targetArea = area;
        startingTargetTreeInitialized = false;
        randomMidTierTree = null;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            if (Rs2Player.isAnimating() || Rs2Player.isMoving() || Rs2Player.isInteracting())
            {
                return;
            }

            int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
            int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
            initializeStartingTargetTree(woodcuttingLevel);

            TreeAreas desiredArea = resolveTargetArea(woodcuttingLevel);
            if (desiredArea != targetArea)
            {
                targetArea = desiredArea;
                debug("Switching woodcutting area to {} for woodcutting level {}", targetArea.getDisplayName(), woodcuttingLevel);
            }

            if (Rs2Inventory.isFull())
            {
                bankLogsOnly();
                return;
            }

            if (upgradeAxe(woodcuttingLevel, attackLevel) && ensureInTargetArea())
            {
                chopForCurrentLevel(woodcuttingLevel);
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean upgradeAxe(int woodcuttingLevel, int attackLevel)
    {
        WoodCuttingReq bestWoodcuttingReq = WoodCuttingReq.bestForWoodcuttingLevel(woodcuttingLevel);
        AxeEquip bestAttackReq = AxeEquip.bestForAttackLevel(attackLevel);

        String desiredAxeName = resolveDesiredAxe(bestWoodcuttingReq, bestAttackReq);
        if (desiredAxeName == null)
        {
            return true;
        }

        boolean hasDesiredEquipped = Rs2Equipment.isWearing(desiredAxeName);
        boolean hasDesiredInventory = Rs2Inventory.hasItem(desiredAxeName);

        if (!hasDesiredEquipped && !hasDesiredInventory)
        {
            ensureInventoryTabOpen();
            if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank())
            {
                return false;
            }

            if (Rs2Bank.isOpen() && Rs2Bank.count(desiredAxeName) > 0)
            {
                Rs2Bank.withdrawOne(desiredAxeName);
                sleepUntil(() -> Rs2Inventory.hasItem(desiredAxeName), 3_000);
            }
            else if (Rs2Bank.isOpen())
            {
                debug("Desired axe {} not available in bank, continuing with current setup", desiredAxeName);
                Rs2Bank.closeBank();
                return true;
            }
        }

        if (Rs2Inventory.hasItem(desiredAxeName) && !Rs2Equipment.isWearing(desiredAxeName))
        {
            if (Rs2Bank.isOpen())
            {
                Rs2Bank.closeBank();
                return false;
            }

            Rs2Inventory.wield(desiredAxeName);
            sleepUntil(() -> Rs2Equipment.isWearing(desiredAxeName), 2_000);
        }

        if (Rs2Bank.isOpen())
        {
            depositOutdatedAxes(desiredAxeName);
            if (!hasOutdatedAxeInInventory(desiredAxeName) && !hasOutdatedAxeEquipped(desiredAxeName))
            {
                Rs2Bank.closeBank();
            }
            return false;
        }

        return true;
    }

    private void depositOutdatedAxes(String desiredAxeName)
    {
        for (String axeName : AXE_NAMES)
        {
            if (axeName.equalsIgnoreCase(desiredAxeName))
            {
                continue;
            }

            if (Rs2Inventory.hasItem(axeName))
            {
                Rs2Bank.depositAll(axeName);
            }
        }
    }

    private boolean hasOutdatedAxeInInventory(String desiredAxeName)
    {
        for (String axeName : AXE_NAMES)
        {
            if (!axeName.equalsIgnoreCase(desiredAxeName) && Rs2Inventory.hasItem(axeName))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasOutdatedAxeEquipped(String desiredAxeName)
    {
        for (String axeName : AXE_NAMES)
        {
            if (!axeName.equalsIgnoreCase(desiredAxeName) && Rs2Equipment.isWearing(axeName))
            {
                return true;
            }
        }
        return false;
    }

    private String resolveDesiredAxe(WoodCuttingReq woodCuttingReq, AxeEquip attackReq)
    {
        int woodcuttingTier = woodCuttingReq.ordinal();
        int attackTier = attackReq.ordinal();
        int bestTier = Math.min(woodcuttingTier, attackTier);
        return AXE_NAMES.get(bestTier);
    }

    private boolean ensureInTargetArea()
    {
        if (targetArea.toWorldArea().contains(Rs2Player.getWorldLocation()))
        {
            return true;
        }

        if (Rs2Player.isMoving())
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if ((now - lastWebWalkAtMs) < WEB_WALK_COOLDOWN_MS)
        {
            return false;
        }

        WorldPoint center = getAreaCenter();
        lastWebWalkAtMs = now;
        if (!Rs2Walker.walkFastCanvas(center))
        {
            Rs2Walker.walkTo(center, 3);
        }
        return false;
    }

    private WorldPoint getAreaCenter()
    {
        int centerX = (targetArea.getSouthWest().getX() + targetArea.getNorthEast().getX()) / 2;
        int centerY = (targetArea.getSouthWest().getY() + targetArea.getNorthEast().getY()) / 2;
        int plane = targetArea.getSouthWest().getPlane();
        return new WorldPoint(centerX, centerY, plane);
    }

    private void ensureInventoryTabOpen()
    {
        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY)
        {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 1_200);
        }
    }

    private void bankLogsOnly()
    {
        ensureInventoryTabOpen();
        if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank())
        {
            return;
        }

        if (Rs2Bank.isOpen())
        {
            if (hasAxeInInventory())
            {
                Rs2Bank.depositAllExcept("axe");
            }
            else
            {
                Rs2Bank.depositAll();
            }
            sleep(300);
            Rs2Bank.closeBank();
        }
    }

    private boolean hasAxeInInventory()
    {
        for (String axeName : AXE_NAMES)
        {
            if (Rs2Inventory.hasItem(axeName))
            {
                return true;
            }
        }

        return false;
    }

    private void chopForCurrentLevel(int woodcuttingLevel)
    {
        if (Rs2Player.isAnimating() || Rs2Player.isMoving() || Rs2Player.isInteracting())
        {
            return;
        }

        TreeLevel treeLevel = getTargetTreeLevel(woodcuttingLevel);
        String treeName = resolveTreeName(treeLevel);
        boolean interactionStarted = Rs2GameObject.interact(treeName, "Chop down");
        if (interactionStarted)
        {
            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 1_200);
        }
    }

    private TreeAreas resolveTargetArea(int woodcuttingLevel)
    {
        TreeLevel treeLevel = getTargetTreeLevel(woodcuttingLevel);
        if (treeLevel == TreeLevel.YEW)
        {
            return TreeAreas.YEW_TREE_VARROCK_PALACE;
        }

        if (treeLevel == TreeLevel.WILLOW)
        {
            return TreeAreas.WILLOW_TREES_DRAYNOR;
        }

        if (treeLevel == TreeLevel.OAK)
        {
            return TreeAreas.OAK_TREE_DRAYNOR;
        }

        return TreeAreas.REGULAR_TREE_VARROCK_WEST;
    }

    private TreeLevel getTargetTreeLevel(int woodcuttingLevel)
    {
        if (startingTargetTreeInitialized && randomMidTierTree != null)
        {
            return randomMidTierTree;
        }

        if (woodcuttingLevel >= TreeLevel.YEW.getRequiredWoodcuttingLevel())
        {
            return TreeLevel.YEW;
        }

        if (woodcuttingLevel >= TreeLevel.WILLOW.getRequiredWoodcuttingLevel())
        {
            return TreeLevel.WILLOW;
        }

        if (woodcuttingLevel >= TreeLevel.OAK.getRequiredWoodcuttingLevel())
        {
            return TreeLevel.OAK;
        }

        return TreeLevel.TREE;
    }

    private boolean shouldRandomizeMidTierTree(int woodcuttingLevel)
    {
        return woodcuttingLevel >= TreeLevel.WILLOW.getRequiredWoodcuttingLevel()
                && woodcuttingLevel < MID_TIER_RANDOM_MAX_LEVEL;
    }

    private void initializeStartingTargetTree(int woodcuttingLevel)
    {
        if (startingTargetTreeInitialized)
        {
            return;
        }

        if (shouldRandomizeMidTierTree(woodcuttingLevel))
        {
            List<TreeLevel> randomOptions = Arrays.asList(TreeLevel.OAK, TreeLevel.WILLOW);
            randomMidTierTree = randomOptions.get(ThreadLocalRandom.current().nextInt(randomOptions.size()));
            debug("Selected starting woodcutting tree {} for woodcutting level {}", randomMidTierTree.getDisplayName(), woodcuttingLevel);
        }
        else
        {
            randomMidTierTree = null;
        }

        startingTargetTreeInitialized = true;
    }

    private String resolveTreeName(TreeLevel treeLevel)
    {
        if (treeLevel == TreeLevel.TREE)
        {
            return "Tree";
        }

        if (treeLevel == TreeLevel.OAK)
        {
            return "Oak tree";
        }

        if (treeLevel == TreeLevel.WILLOW)
        {
            return "Willow tree";
        }

        return "Yew";
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            log.info(message, args);
        }
    }

    @Override
    public void shutdown()
    {
        startingTargetTreeInitialized = false;
        randomMidTierTree = null;
        super.shutdown();
    }
}
