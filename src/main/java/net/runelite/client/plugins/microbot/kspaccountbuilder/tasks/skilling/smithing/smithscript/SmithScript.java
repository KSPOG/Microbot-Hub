package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithscript;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.recipes.SmithRecipe;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smitharea.SmithArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithlevels.SmithLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.tool.SmithTool;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class SmithScript extends Script
{
    private static final int INVENTORY_SLOTS = 28;
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3_000;
    private static final int ANVIL_INTERACT_COOLDOWN_MS = 2_000;
    private static final int SMITH_START_GRACE_MS = 2_500;
    private static final int SMITH_ANIMATION_COOLDOWN_MS = 1_800;
    private static final int SMITHING_WIDGET_GROUP_ID = 312;
    private static final int SMITHING_WIDGET_CONTAINER_CHILD_ID = 1;
    private static final int SMITHING_ALL_BUTTON_CHILD_ID = 7;
    private static final int ANVIL_MAKE_VARBIT_PLAYER = 2224;

    private long lastWebWalkAtMs;
    private long lastAnvilInteractAtMs;
    private long awaitingSmithStartAtMs;
    private long lastSmithAnimationAtMs;
    private boolean expectingSmithXpDrop;
    private boolean debugLogging;

    @Getter
    private SmithArea targetArea = SmithArea.SMITH_AREA_VARROCK_WEST_ANVIL;

    @Getter
    private SmithRecipe targetRecipe = SmithRecipe.BRONZE_DAGGER;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public boolean run(SmithArea area)
    {
        shutdown();
        targetArea = area;
        targetRecipe = SmithRecipe.BRONZE_DAGGER;
        expectingSmithXpDrop = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            selectTargetRecipe();
            if (targetRecipe == null)
            {
                debug("No smithing recipe available for the current level");
                return;
            }

            if (!ensureToolAndBarsForTargetRecipe(targetRecipe))
            {
                debug("Unable to prepare smithing inventory for {}", targetRecipe.getDisplayName());
                return;
            }

            if (!ensureInTargetArea())
            {
                return;
            }

            smithAtAnvil(targetRecipe);
            debug("SmithScript active | area={} | recipe={}", targetArea.name(), targetRecipe.name());
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void selectTargetRecipe()
    {
        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        SmithRecipe bestRecipe = resolveBestCraftableRecipe(smithingLevel);
        if (bestRecipe == null)
        {
            bestRecipe = resolveBestUnlockedRecipe(smithingLevel);
        }

        if (bestRecipe != null && bestRecipe != targetRecipe)
        {
            targetRecipe = bestRecipe;
            debug("Selected smithing recipe {} at smithing level {}", targetRecipe.getDisplayName(), smithingLevel);
        }
    }

    private SmithRecipe resolveBestCraftableRecipe(int smithingLevel)
    {
        SmithLevels[] levels = SmithLevels.values();
        for (int i = levels.length - 1; i >= 0; i--)
        {
            SmithLevels smithLevel = levels[i];
            if (smithingLevel < smithLevel.getRequiredLevel())
            {
                continue;
            }

            SmithRecipe recipe = resolveRecipe(smithLevel);
            if (recipe == null)
            {
                continue;
            }

            if (hasRequiredBarsInInventory(recipe) || getCraftableProductsFromBank(recipe) > 0)
            {
                return recipe;
            }
        }

        return null;
    }

    private SmithRecipe resolveBestUnlockedRecipe(int smithingLevel)
    {
        SmithLevels[] levels = SmithLevels.values();
        for (int i = levels.length - 1; i >= 0; i--)
        {
            SmithLevels smithLevel = levels[i];
            if (smithingLevel < smithLevel.getRequiredLevel())
            {
                continue;
            }

            SmithRecipe recipe = resolveRecipe(smithLevel);
            if (recipe != null)
            {
                return recipe;
            }
        }

        return null;
    }

    private SmithRecipe resolveRecipe(SmithLevels smithLevel)
    {
        try
        {
            return SmithRecipe.valueOf(smithLevel.name());
        }
        catch (IllegalArgumentException ex)
        {
            debug("No SmithRecipe entry found for {}", smithLevel.name());
            return null;
        }
    }

    private boolean ensureToolAndBarsForTargetRecipe(SmithRecipe recipe)
    {
        if (hasRequiredInventory(recipe))
        {
            return true;
        }

        expectingSmithXpDrop = false;
        awaitingSmithStartAtMs = 0L;

        if (Rs2Bank.isOpen())
        {
            return prepareAndCloseBank(recipe);
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

    private boolean prepareAndCloseBank(SmithRecipe recipe)
    {
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        if (!prepareSmithingInventory(recipe))
        {
            return false;
        }

        if (!hasRequiredInventory(recipe))
        {
            return false;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 1_500);
        return false;
    }

    private boolean prepareSmithingInventory(SmithRecipe recipe)
    {
        String hammerName = SmithTool.HAMMER.getDisplayName();
        String barName = getBarName(recipe);
        int hammerItemId = SmithTool.HAMMER.getItemId();

        if (Rs2Inventory.hasItem(hammerName))
        {
            Rs2Bank.depositAllExcept(hammerItemId);
        }
        else
        {
            Rs2Bank.depositAll();
        }
        sleep(200);

        if (!Rs2Inventory.hasItem(hammerName))
        {
            if (Rs2Bank.count(hammerName) <= 0)
            {
                debug("Hammer not found in bank");
                return false;
            }

            if (!Rs2Bank.withdrawOne(hammerName))
            {
                return false;
            }

            sleepUntil(() -> Rs2Inventory.hasItem(hammerName), 2_000);
        }

        int productsToWithdraw = getProductsToWithdraw(recipe);
        if (productsToWithdraw <= 0)
        {
            debug("Not enough bars in bank for {}", recipe.getDisplayName());
            return false;
        }

        int barsToWithdraw = productsToWithdraw * recipe.getBarRequirement();
        if (!Rs2Bank.withdrawX(barName, barsToWithdraw))
        {
            return false;
        }

        sleepUntil(() -> Rs2Inventory.count(barName) == barsToWithdraw, 2_000);
        return Rs2Inventory.count(barName) == barsToWithdraw;
    }

    private int getProductsToWithdraw(SmithRecipe recipe)
    {
        String barName = getBarName(recipe);
        int slotsReserved = 1;
        int maxBarsForInventory = INVENTORY_SLOTS - slotsReserved;
        int maxProductsForInventory = maxBarsForInventory / recipe.getBarRequirement();
        int maxProductsFromBank = Math.max(0, Rs2Bank.count(barName)) / recipe.getBarRequirement();
        return Math.min(maxProductsForInventory, maxProductsFromBank);
    }

    private boolean hasRequiredInventory(SmithRecipe recipe)
    {
        return hasHammerInInventory() && hasRequiredBarsInInventory(recipe);
    }

    private boolean hasHammerInInventory()
    {
        return Rs2Inventory.hasItem(SmithTool.HAMMER.getDisplayName());
    }

    private boolean hasRequiredBarsInInventory(SmithRecipe recipe)
    {
        return Rs2Inventory.count(getBarName(recipe)) >= recipe.getBarRequirement();
    }

    private int getCraftableProductsFromBank(SmithRecipe recipe)
    {
        return Math.max(0, Rs2Bank.count(getBarName(recipe))) / recipe.getBarRequirement();
    }

    private String getBarName(SmithRecipe recipe)
    {
        String[] words = recipe.getDisplayName().split(" ");
        return words[0] + " bar";
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

    private void smithAtAnvil(SmithRecipe recipe)
    {
        if (expectingSmithXpDrop && Rs2Player.waitForXpDrop(Skill.SMITHING, 7_500))
        {
            lastSmithAnimationAtMs = System.currentTimeMillis();
            return;
        }

        if (isSmithingWidgetOpen())
        {
            handleSmithingSelection(recipe);
            return;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving() || Rs2Player.isInteracting())
        {
            lastSmithAnimationAtMs = System.currentTimeMillis();
            return;
        }

        long sinceLastSmithAnimation = System.currentTimeMillis() - lastSmithAnimationAtMs;
        if (sinceLastSmithAnimation < SMITH_ANIMATION_COOLDOWN_MS)
        {
            return;
        }

        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            return;
        }

        if (handleSmithingSelection(recipe))
        {
            return;
        }

        if (isWaitingForSmithStart())
        {
            return;
        }

        if (Rs2Player.isMoving())
        {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastAnvilInteractAtMs) < ANVIL_INTERACT_COOLDOWN_MS)
        {
            return;
        }

        boolean started = Rs2GameObject.interact("Anvil", "Smith");
        if (!started)
        {
            return;
        }

        lastAnvilInteractAtMs = now;
        awaitingSmithStartAtMs = System.currentTimeMillis();
        debug("Interacted with anvil to smith {}", recipe.getDisplayName());
    }

    private boolean handleSmithingSelection(SmithRecipe recipe)
    {
        if (!isSmithingWidgetOpen())
        {
            return false;
        }

        if (isWaitingForSmithStart())
        {
            return true;
        }

        int inventoryBarCount = Rs2Inventory.count(getBarName(recipe));
        if (Microbot.getVarbitPlayerValue(ANVIL_MAKE_VARBIT_PLAYER) < inventoryBarCount)
        {
            Rs2Widget.clickWidget(SMITHING_WIDGET_GROUP_ID, SMITHING_ALL_BUTTON_CHILD_ID);
            sleep(150);
        }

        boolean selectedRecipe = Rs2Widget.clickWidget(SMITHING_WIDGET_GROUP_ID, getSmithingChildId(recipe));
        if (!selectedRecipe)
        {
            debug("Failed to select smithing menu option {}", recipe.getDisplayName());
            return true;
        }

        sleep(150);
        awaitingSmithStartAtMs = System.currentTimeMillis();
        expectingSmithXpDrop = true;
        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 2_500);
        return true;
    }

    private boolean isSmithingWidgetOpen()
    {
        return Rs2Widget.isSmithingWidgetOpen()
                || Rs2Widget.getWidget(SMITHING_WIDGET_GROUP_ID, SMITHING_WIDGET_CONTAINER_CHILD_ID) != null;
    }

    private int getSmithingChildId(SmithRecipe recipe)
    {
        switch (recipe)
        {
            case BRONZE_DAGGER:
                return 9;
            case BRONZE_SCIMITAR:
            case IRON_SCIMITAR:
            case STEEL_SCIMITAR:
                return 11;
            case BRONZE_WARHAMMER:
            case IRON_WARHAMMER:
            case STEEL_WARHAMMER:
                return 16;
            case BRONZE_PLATEBODY:
            case IRON_PLATEBODY:
            case STEEL_PLATEBODY:
                return 22;
            default:
                return 9;
        }
    }

    private boolean isWaitingForSmithStart()
    {
        if (awaitingSmithStartAtMs == 0L)
        {
            return false;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            return true;
        }

        long elapsed = System.currentTimeMillis() - awaitingSmithStartAtMs;
        if (elapsed < SMITH_START_GRACE_MS)
        {
            return true;
        }

        awaitingSmithStartAtMs = 0L;
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
        awaitingSmithStartAtMs = 0L;
        lastSmithAnimationAtMs = 0L;
        lastAnvilInteractAtMs = 0L;
        lastWebWalkAtMs = 0L;
        expectingSmithXpDrop = false;
        super.shutdown();
    }
}
