package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltscript;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.oresreq.ReqOres;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea.SmeltArea;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class SmeltScript extends Script
{
    private static final int INVENTORY_SLOTS = 28;
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3_000;
    private static final int FURNACE_INTERACT_COOLDOWN_MS = 2_000;
    private static final int SMELT_START_GRACE_MS = 2_500;
    private static final int SMELT_ANIMATION_COOLDOWN_MS = 1_800;

    private long lastWebWalkAtMs;
    private long lastFurnaceInteractAtMs;
    private long awaitingSmeltStartAtMs;
    private long lastSmeltAnimationAtMs;

    @Getter
    private SmeltArea targetArea = SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE;

    @Getter
    private BarLevels targetBar = BarLevels.BRONZE;

    private boolean debugLogging;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public boolean run(SmeltArea area, BarLevels fallbackBarLevel)
    {
        shutdown();
        targetArea = area;
        targetBar = fallbackBarLevel;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            selectTargetBar(fallbackBarLevel);

            if (!ensureOreInventoryForTargetBar(targetBar))
            {
                debug("Unable to prepare ore inventory for {} yet", targetBar.getDisplayName());
                return;
            }

            if (!ensureInTargetArea())
            {
                return;
            }

            smeltAtFurnace(targetBar);
            debug("SmeltScript active | area={} | targetBar={}", targetArea.name(), targetBar.name());
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void selectTargetBar(BarLevels fallbackBarLevel)
    {
        BarLevels inventoryBar = resolveInventorySmeltableBar();
        if (inventoryBar != null)
        {
            if (targetBar != inventoryBar)
            {
                targetBar = inventoryBar;
                debug("Keeping inventory-selected bar {}", targetBar.getDisplayName());
            }
            return;
        }

        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        BarLevels bestBar = resolveBestSmeltableBar(smithingLevel);
        if (bestBar != null)
        {
            if (targetBar != bestBar)
            {
                targetBar = bestBar;
                debug("Selected best smeltable bar {} at smithing level {}", targetBar.getDisplayName(), smithingLevel);
            }
            return;
        }

        targetBar = fallbackBarLevel;
        debug("No smeltable bar found from bank ores at smithing level {}, using fallback {}", smithingLevel, fallbackBarLevel.getDisplayName());
    }

    private BarLevels resolveInventorySmeltableBar()
    {
        ReqOres currentReq = ReqOres.valueOf(targetBar.name());
        if (hasBalancedOreInventory(currentReq))
        {
            return targetBar;
        }

        BarLevels[] bars = BarLevels.values();
        for (int i = bars.length - 1; i >= 0; i--)
        {
            BarLevels bar = bars[i];
            ReqOres req = ReqOres.valueOf(bar.name());
            if (hasBalancedOreInventory(req))
            {
                return bar;
            }
        }

        return null;
    }

    private BarLevels resolveBestSmeltableBar(int smithingLevel)
    {
        BarLevels[] bars = BarLevels.values();
        for (int i = bars.length - 1; i >= 0; i--)
        {
            BarLevels bar = bars[i];
            if (smithingLevel < bar.getRequiredSmithingLevel())
            {
                continue;
            }

            if (getCraftableBarsFromBank(bar) > 0)
            {
                return bar;
            }
        }

        return null;
    }

    private int getCraftableBarsFromBank(BarLevels bar)
    {
        ReqOres req = ReqOres.valueOf(bar.name());

        int primaryCount = Math.max(0, Rs2Bank.count(req.getPrimaryOreName()));
        int primaryBars = primaryCount / req.getPrimaryOreAmount();

        if (!req.hasSecondaryOre())
        {
            return primaryBars;
        }

        int secondaryCount = Math.max(0, Rs2Bank.count(req.getSecondaryOreName()));
        int secondaryBars = secondaryCount / req.getSecondaryOreAmount();
        return Math.min(primaryBars, secondaryBars);
    }

    private boolean ensureOreInventoryForTargetBar(BarLevels bar)
    {
        ReqOres req = ReqOres.valueOf(bar.name());
        if (hasBalancedOreInventory(req))
        {
            return true;
        }

        if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank())
        {
            return false;
        }

        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        Rs2Bank.depositAll();
        sleep(250);

        int barsToWithdraw = getBarsToWithdrawForInventory(req);
        if (barsToWithdraw <= 0)
        {
            debug("Not enough ores in bank to withdraw for {}", bar.getDisplayName());
            return false;
        }

        int primaryToWithdraw = barsToWithdraw * req.getPrimaryOreAmount();
        int secondaryToWithdraw = req.hasSecondaryOre() ? barsToWithdraw * req.getSecondaryOreAmount() : 0;

        if (!prepareExactOreInventory(req, primaryToWithdraw, secondaryToWithdraw))
        {
            debug("Failed to prepare exact ore amounts for {} (primary={}, secondary={})", bar.getDisplayName(), primaryToWithdraw, secondaryToWithdraw);
            return false;
        }

        return hasRequiredOresInInventory(req);
    }

    private boolean hasBalancedOreInventory(ReqOres req)
    {
        if (!hasRequiredOresInInventory(req))
        {
            return false;
        }

        if (!req.hasSecondaryOre())
        {
            return true;
        }

        int primaryCount = Rs2Inventory.count(req.getPrimaryOreName());
        int secondaryCount = Rs2Inventory.count(req.getSecondaryOreName());
        return primaryCount * req.getSecondaryOreAmount() == secondaryCount * req.getPrimaryOreAmount();
    }

    private boolean prepareExactOreInventory(ReqOres req, int primaryTargetAmount, int secondaryTargetAmount)
    {
        for (int attempt = 0; attempt < 4; attempt++)
        {
            adjustOreAmount(req.getPrimaryOreName(), primaryTargetAmount);
            if (req.hasSecondaryOre())
            {
                adjustOreAmount(req.getSecondaryOreName(), secondaryTargetAmount);
            }

            int currentPrimary = Rs2Inventory.count(req.getPrimaryOreName());
            int currentSecondary = req.hasSecondaryOre() ? Rs2Inventory.count(req.getSecondaryOreName()) : 0;
            if (currentPrimary == primaryTargetAmount && (!req.hasSecondaryOre() || currentSecondary == secondaryTargetAmount))
            {
                return true;
            }
        }

        return false;
    }

    private void adjustOreAmount(String oreName, int targetAmount)
    {
        int currentAmount = Rs2Inventory.count(oreName);
        int diff = targetAmount - currentAmount;
        if (diff == 0)
        {
            return;
        }

        if (diff > 0)
        {
            Rs2Bank.withdrawX(oreName, diff, true);
        }
        else
        {
            Rs2Bank.depositX(oreName, Math.abs(diff));
        }

        sleep(150);
    }

    private int getBarsToWithdrawForInventory(ReqOres req)
    {
        int maxBarsFromPrimary = Math.max(0, Rs2Bank.count(req.getPrimaryOreName())) / req.getPrimaryOreAmount();
        if (maxBarsFromPrimary <= 0)
        {
            return 0;
        }

        int maxBarsFromBank = maxBarsFromPrimary;
        if (req.hasSecondaryOre())
        {
            int maxBarsFromSecondary = Math.max(0, Rs2Bank.count(req.getSecondaryOreName())) / req.getSecondaryOreAmount();
            maxBarsFromBank = Math.min(maxBarsFromPrimary, maxBarsFromSecondary);
        }

        int oresPerBar = req.getPrimaryOreAmount() + (req.hasSecondaryOre() ? req.getSecondaryOreAmount() : 0);
        int maxBarsFromInventory = INVENTORY_SLOTS / oresPerBar;
        return Math.min(maxBarsFromBank, maxBarsFromInventory);
    }

    private boolean hasRequiredOresInInventory(ReqOres req)
    {
        int primaryCount = Rs2Inventory.count(req.getPrimaryOreName());
        if (primaryCount < req.getPrimaryOreAmount())
        {
            return false;
        }

        if (!req.hasSecondaryOre())
        {
            return true;
        }

        int secondaryCount = Rs2Inventory.count(req.getSecondaryOreName());
        return secondaryCount >= req.getSecondaryOreAmount();
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
            Rs2Walker.walkTo(center, 2);
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

    private void smeltAtFurnace(BarLevels bar)
    {
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            lastSmeltAnimationAtMs = System.currentTimeMillis();
            return;
        }

        long sinceLastSmeltAnimation = System.currentTimeMillis() - lastSmeltAnimationAtMs;
        if (sinceLastSmeltAnimation < SMELT_ANIMATION_COOLDOWN_MS)
        {
            return;
        }

        if (isWaitingForSmeltStart())
        {
            return;
        }

        if (Rs2Player.isMoving())
        {
            return;
        }

        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            return;
        }

        if (handleSmeltSelection(bar))
        {
            return;
        }

        if (handleProductionWidget())
        {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastFurnaceInteractAtMs) < FURNACE_INTERACT_COOLDOWN_MS)
        {
            return;
        }

        lastFurnaceInteractAtMs = now;
        boolean started = Rs2GameObject.interact("Furnace", "Smelt");
        if (!started)
        {
            return;
        }

        awaitingSmeltStartAtMs = System.currentTimeMillis();
        debug("Interacted with furnace to smelt {}", bar.getDisplayName());
    }

    private boolean handleSmeltSelection(BarLevels bar)
    {
        boolean smeltSelectionOpened = Rs2Widget.sleepUntilHasWidgetText("What would you like to smelt?", 270, 5, false, 600);
        if (!smeltSelectionOpened)
        {
            return false;
        }

        boolean clickedBar = Rs2Widget.clickWidget(bar.getDisplayName());
        if (!clickedBar)
        {
            debug("Failed to click smelt option {}", bar.getDisplayName());
            return true;
        }

        Rs2Widget.sleepUntilHasNotWidgetText("What would you like to smelt?", 270, 5, false, 3_000);
        handleProductionWidget();
        return true;
    }

    private boolean handleProductionWidget()
    {
        if (!Rs2Widget.isProductionWidgetOpen())
        {
            boolean productionOpened = sleepUntil(Rs2Widget::isProductionWidgetOpen, 800);
            if (!productionOpened)
            {
                return false;
            }
        }

        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        awaitingSmeltStartAtMs = System.currentTimeMillis();
        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 2_500);
        return true;
    }

    private boolean isWaitingForSmeltStart()
    {
        if (awaitingSmeltStartAtMs == 0L)
        {
            return false;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            return true;
        }

        long elapsed = System.currentTimeMillis() - awaitingSmeltStartAtMs;
        if (elapsed < SMELT_START_GRACE_MS)
        {
            return true;
        }

        awaitingSmeltStartAtMs = 0L;
        return false;
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            log.debug(message, args);
        }
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
    }
}
