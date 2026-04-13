package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.firemakingscript;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.fmarea.FireArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.loglevels.LogsLvl;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class FireMakingScript extends Script
{
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3_000;
    private static final int FIRE_INTERACT_COOLDOWN_MS = 2_000;
    private static final int FIRE_START_GRACE_MS = 2_500;
    private static final int CAMPFIRE_DISTANCE = 6;
    private static final int NORMAL_FIRE_ID = 26185;
    private static final int FORESTERS_CAMPFIRE_ID = 49927;
    private static final String TINDERBOX_NAME = "Tinderbox";
    private static final String BURN_PROMPT_TEXT = "How many would you like to burn?";

    @Getter
    private FireArea targetArea = FireArea.FM_AREA_DRAYNOR_BANK;

    private long lastWebWalkAtMs;
    private long lastFireInteractAtMs;
    private long awaitingFireStartAtMs;
    private boolean expectingFiremakingXpDrop;
    private boolean debugLogging;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public boolean run(FireArea area)
    {
        shutdown();
        targetArea = area;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            String targetLogName = resolveTargetLogName();
            if (targetLogName == null)
            {
                debug("No firemaking log target available for current level");
                return;
            }

            if (expectingFiremakingXpDrop && Rs2Player.waitForXpDrop(Skill.FIREMAKING, 4_500))
            {
                debug("Firemaking in progress with {}", targetLogName);
                return;
            }

            if (handleBurnPrompt())
            {
                return;
            }

            if (!ensureInTargetArea())
            {
                return;
            }

            WorldPoint fireLocation = findActiveFireLocationInTargetArea();
            if (!ensureSupplies(targetLogName, fireLocation != null))
            {
                return;
            }

            if (Rs2Player.isAnimating() || Rs2Player.isMoving() || Rs2Player.isInteracting())
            {
                return;
            }

            if (fireLocation != null)
            {
                useCampfire(getTargetLogId(targetLogName), fireLocation);
                return;
            }

            buildFire(targetLogName);
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private String resolveTargetLogName()
    {
        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        LogsLvl bestMatch = null;
        for (LogsLvl logsLvl : LogsLvl.values())
        {
            if (firemakingLevel >= logsLvl.getRequiredLevel())
            {
                bestMatch = logsLvl;
            }
        }

        return bestMatch != null ? bestMatch.getDisplayName() : null;
    }

    private boolean ensureSupplies(String targetLogName, boolean hasActiveFire)
    {
        if (hasLogsForCurrentTarget(targetLogName) && (hasActiveFire || Rs2Inventory.hasItem(TINDERBOX_NAME)))
        {
            return true;
        }

        awaitingFireStartAtMs = 0L;
        expectingFiremakingXpDrop = false;

        if (Rs2Bank.isOpen())
        {
            return prepareSuppliesFromBank(targetLogName, hasActiveFire);
        }

        if (Rs2Player.isMoving() || Rs2Player.isInteracting())
        {
            return false;
        }

        if (Rs2Bank.openBank())
        {
            sleepUntil(Rs2Bank::isOpen, 3_000);
            return false;
        }

        if (Rs2Bank.walkToBankAndUseBank())
        {
            return false;
        }

        return false;
    }

    private boolean prepareSuppliesFromBank(String targetLogName, boolean hasActiveFire)
    {
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        if (Rs2Inventory.hasItem(TINDERBOX_NAME) || !hasActiveFire)
        {
            Rs2Bank.depositAllExcept(TINDERBOX_NAME);
        }
        else
        {
            Rs2Bank.depositAll();
        }
        sleep(200);

        if (!hasActiveFire && !Rs2Inventory.hasItem(TINDERBOX_NAME))
        {
            if (Rs2Bank.count(TINDERBOX_NAME) <= 0)
            {
                debug("No tinderbox available in bank");
                return false;
            }

            if (!Rs2Bank.withdrawOne(TINDERBOX_NAME))
            {
                return false;
            }

            sleepUntil(() -> Rs2Inventory.hasItem(TINDERBOX_NAME), 2_000);
        }

        if (Rs2Bank.count(targetLogName) <= 0)
        {
            debug("No {} available in bank", targetLogName);
            return false;
        }

        if (!Rs2Bank.withdrawAll(targetLogName))
        {
            return false;
        }

        sleepUntil(() -> Rs2Inventory.hasItem(targetLogName), 2_000);
        if (!Rs2Inventory.hasItem(targetLogName))
        {
            debug("Failed to withdraw {}", targetLogName);
            return false;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 1_500);
        return false;
    }

    private boolean hasLogsForCurrentTarget(String targetLogName)
    {
        return Rs2Inventory.hasItem(targetLogName);
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

        lastWebWalkAtMs = now;
        WorldPoint center = getAreaCenter();
        if (!Rs2Walker.walkFastCanvas(center))
        {
            Rs2Walker.walkTo(center, 2);
        }
        return false;
    }

    private void useCampfire(int targetLogId, WorldPoint fireLocation)
    {
        if (Rs2Player.distanceTo(fireLocation) > CAMPFIRE_DISTANCE)
        {
            Rs2Walker.walkTo(fireLocation, 1);
            return;
        }

        if (isWaitingForFireStart())
        {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastFireInteractAtMs) < FIRE_INTERACT_COOLDOWN_MS)
        {
            return;
        }

        TileObject fireTile = Rs2GameObject.findGameObjectByLocation(fireLocation);
        if (fireTile == null || !isValidFireId(fireTile.getId()))
        {
            return;
        }

        boolean interacted = Rs2Inventory.useItemOnObject(targetLogId, fireTile.getId());
        if (!interacted)
        {
            return;
        }

        lastFireInteractAtMs = now;
        awaitingFireStartAtMs = now;
        sleepUntil(() -> !Rs2Player.isMoving() && isBurnPromptOpen(), 5_000);
    }

    private void buildFire(String targetLogName)
    {
        if (!Rs2Inventory.hasItem(TINDERBOX_NAME))
        {
            debug("Missing tinderbox for fallback firemaking");
            return;
        }

        if (isWaitingForFireStart())
        {
            return;
        }

        WorldPoint center = getAreaCenter();
        if (Rs2Player.distanceTo(center) > 1)
        {
            Rs2Walker.walkTo(center, 1);
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastFireInteractAtMs) < FIRE_INTERACT_COOLDOWN_MS)
        {
            return;
        }

        Rs2Inventory.combine(TINDERBOX_NAME, targetLogName);
        lastFireInteractAtMs = now;
        awaitingFireStartAtMs = now;
        expectingFiremakingXpDrop = true;
        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 2_500);
    }

    private boolean handleBurnPrompt()
    {
        if (!isBurnPromptOpen())
        {
            return false;
        }

        if (Rs2Player.isMoving())
        {
            return true;
        }

        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        awaitingFireStartAtMs = System.currentTimeMillis();
        expectingFiremakingXpDrop = true;
        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 2_000);
        return true;
    }

    private boolean isBurnPromptOpen()
    {
        return Rs2Widget.findWidget(BURN_PROMPT_TEXT, null, false) != null;
    }

    private boolean isWaitingForFireStart()
    {
        if (awaitingFireStartAtMs == 0L)
        {
            return false;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            return true;
        }

        long elapsed = System.currentTimeMillis() - awaitingFireStartAtMs;
        if (elapsed < FIRE_START_GRACE_MS)
        {
            return true;
        }

        awaitingFireStartAtMs = 0L;
        return false;
    }

    private WorldPoint findActiveFireLocationInTargetArea()
    {
        WorldArea area = targetArea.toWorldArea();
        for (int x = targetArea.getSouthWest().getX(); x <= targetArea.getNorthEast().getX(); x++)
        {
            for (int y = targetArea.getSouthWest().getY(); y <= targetArea.getNorthEast().getY(); y++)
            {
                WorldPoint location = new WorldPoint(x, y, targetArea.getSouthWest().getPlane());
                if (!area.contains(location))
                {
                    continue;
                }

                TileObject fireTile = Rs2GameObject.findGameObjectByLocation(location);
                if (fireTile != null && isValidFireId(fireTile.getId()))
                {
                    return location;
                }
            }
        }

        return null;
    }

    private boolean isValidFireId(int objectId)
    {
        return objectId == NORMAL_FIRE_ID || objectId == FORESTERS_CAMPFIRE_ID;
    }

    private WorldPoint getAreaCenter()
    {
        int centerX = (targetArea.getSouthWest().getX() + targetArea.getNorthEast().getX()) / 2;
        int centerY = (targetArea.getSouthWest().getY() + targetArea.getNorthEast().getY()) / 2;
        int plane = targetArea.getSouthWest().getPlane();
        return new WorldPoint(centerX, centerY, plane);
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            log.info(message, args);
        }
    }

    private int getTargetLogId(String targetLogName)
    {
        if (LogsLvl.OAK_LOGS.getDisplayName().equalsIgnoreCase(targetLogName))
        {
            return ItemID.OAK_LOGS;
        }

        if (LogsLvl.WILLOW_LOGS.getDisplayName().equalsIgnoreCase(targetLogName))
        {
            return ItemID.WILLOW_LOGS;
        }

        return ItemID.LOGS;
    }

    @Override
    public void shutdown()
    {
        lastWebWalkAtMs = 0L;
        lastFireInteractAtMs = 0L;
        awaitingFireStartAtMs = 0L;
        expectingFiremakingXpDrop = false;
        super.shutdown();
    }
}
