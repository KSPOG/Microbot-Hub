package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.equiplevels.PickaxeEquip;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.levelreqmining.MiningReq;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.rocklevel.RockLevel;
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
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class MiningScript extends Script
{
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3_000;
    private static final List<String> PICKAXE_NAMES = Arrays.asList(
        "Bronze pickaxe",
        "Iron pickaxe",
        "Steel pickaxe",
        "Black pickaxe",
        "Mithril pickaxe",
        "Adamant pickaxe",
        "Rune pickaxe"
    );

    @Getter
    private Areas targetArea = Areas.TIN_COPPER_VARROCK_EAST;

    private boolean debugLogging;
    private long lastWebWalkAtMs;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public boolean run(Areas area)
    {
        shutdown();
        targetArea = area;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            if (Rs2Player.isAnimating())
            {
                return;
            }

            int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
            int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);

            Areas desiredArea = resolveTargetArea(miningLevel);
            if (desiredArea != targetArea)
            {
                targetArea = desiredArea;
                debug("Switching mining area to {} for mining level {}", targetArea.getDisplayName(), miningLevel);
            }

            if (Rs2Inventory.isFull())
            {
                bankOresOnly();
                return;
            }

            if (upgradePickaxe(miningLevel, attackLevel) && ensureInTargetArea())
            {
                mineForCurrentLevel(miningLevel);
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean upgradePickaxe(int miningLevel, int attackLevel)
    {
        MiningReq bestMiningReq = MiningReq.bestForMiningLevel(miningLevel);
        PickaxeEquip bestAttackReq = PickaxeEquip.bestForAttackLevel(attackLevel);

        String desiredPickaxeName = resolveDesiredPickaxe(bestMiningReq, bestAttackReq);
        if (desiredPickaxeName == null)
        {
            return true;
        }

        boolean hasDesiredEquipped = Rs2Equipment.isWearing(desiredPickaxeName);
        boolean hasDesiredInventory = Rs2Inventory.hasItem(desiredPickaxeName);

        if (!hasDesiredEquipped && !hasDesiredInventory)
        {
            ensureInventoryTabOpen();
            if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank())
            {
                return false;
            }

            if (Rs2Bank.isOpen() && Rs2Bank.count(desiredPickaxeName) > 0)
            {
                Rs2Bank.withdrawOne(desiredPickaxeName);
                sleepUntil(() -> Rs2Inventory.hasItem(desiredPickaxeName), 3_000);
            }
            else if (Rs2Bank.isOpen())
            {
                debug("Desired pickaxe {} not available in bank, continuing with current setup", desiredPickaxeName);
                Rs2Bank.closeBank();
                return true;
            }
        }

        if (Rs2Inventory.hasItem(desiredPickaxeName) && !Rs2Equipment.isWearing(desiredPickaxeName))
        {
            Rs2Inventory.wield(desiredPickaxeName);
            sleepUntil(() -> Rs2Equipment.isWearing(desiredPickaxeName), 2_000);
        }

        if (Rs2Bank.isOpen())
        {
            depositOutdatedPickaxes(desiredPickaxeName);
            if (!hasOutdatedPickaxeInInventory(desiredPickaxeName) && !hasOutdatedPickaxeEquipped(desiredPickaxeName))
            {
                Rs2Bank.closeBank();
            }
            return false;
        }

        return true;
    }

    private void depositOutdatedPickaxes(String desiredPickaxeName)
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (pickaxeName.equalsIgnoreCase(desiredPickaxeName))
            {
                continue;
            }

            if (Rs2Inventory.hasItem(pickaxeName))
            {
                Rs2Bank.depositAll(pickaxeName);
            }
        }
    }

    private boolean hasOutdatedPickaxeInInventory(String desiredPickaxeName)
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (!pickaxeName.equalsIgnoreCase(desiredPickaxeName) && Rs2Inventory.hasItem(pickaxeName))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasOutdatedPickaxeEquipped(String desiredPickaxeName)
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (!pickaxeName.equalsIgnoreCase(desiredPickaxeName) && Rs2Equipment.isWearing(pickaxeName))
            {
                return true;
            }
        }
        return false;
    }

    private String resolveDesiredPickaxe(MiningReq miningReq, PickaxeEquip attackReq)
    {
        int miningTier = miningReq.ordinal();
        int attackTier = attackReq.ordinal();
        int bestTier = Math.min(miningTier, attackTier);
        return PICKAXE_NAMES.get(bestTier);
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

    private void bankOresOnly()
    {
        ensureInventoryTabOpen();
        if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank())
        {
            return;
        }

        if (Rs2Bank.isOpen())
        {
            Rs2Bank.depositAllExcept("pickaxe");
            sleep(300);
            Rs2Bank.closeBank();
        }
    }

    private void mineForCurrentLevel(int miningLevel)
    {
        if (Rs2Player.isAnimating())
        {
            return;
        }

        String oreName = getTargetOreName(miningLevel);
        boolean interactionStarted;

        if (targetArea == Areas.TIN_COPPER_VARROCK_EAST)
        {
            interactionStarted = Rs2GameObject.interact("Rocks", "Mine");
        }
        else
        {
            String oreRockName = oreName + " rocks";
            interactionStarted = Rs2GameObject.interact(oreRockName, "Mine");
        }

        if (interactionStarted)
        {
            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 1_200);
        }
    }

    private Areas resolveTargetArea(int miningLevel)
    {
        if (miningLevel >= RockLevel.COAL.getRequiredMiningLevel())
        {
            return Areas.COAL_BARBARIAN_VILLAGE;
        }

        if (miningLevel >= RockLevel.SILVER.getRequiredMiningLevel())
        {
            return Areas.SILVER_VARROCK_WEST;
        }

        if (miningLevel >= RockLevel.IRON.getRequiredMiningLevel())
        {
            return Areas.IRON_VARROCK_EAST;
        }

        return Areas.TIN_COPPER_VARROCK_EAST;
    }

    private String getTargetOreName(int miningLevel)
    {
        if (miningLevel >= RockLevel.COAL.getRequiredMiningLevel())
        {
            return RockLevel.COAL.getDisplayName();
        }

        if (miningLevel >= RockLevel.SILVER.getRequiredMiningLevel())
        {
            return RockLevel.SILVER.getDisplayName();
        }

        if (miningLevel >= RockLevel.IRON.getRequiredMiningLevel())
        {
            return RockLevel.IRON.getDisplayName();
        }

        return getStarterRockName();
    }

    private String getStarterRockName()
    {
        int tinCount = Rs2Inventory.count("Tin ore");
        int copperCount = Rs2Inventory.count("Copper ore");

        if (tinCount <= copperCount)
        {
            return RockLevel.TIN.getDisplayName();
        }

        return RockLevel.COPPER.getDisplayName();
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
        super.shutdown();
    }
}
