package net.runelite.client.plugins.microbot.f2pcooker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class F2pCookerScript extends Script {
    private static final WorldPoint EDGEVILLE_STOVE_POINT = new WorldPoint(3078, 3493, 0);
    private static final int EDGEVILLE_STOVE_ID = 12269;
    private static final int STOVE_AREA_MAX_X = 3081;
    private static final int STOVE_AREA_MAX_Y = 3496;
    private static final int STOVE_AREA_MIN_X = 3077;
    private static final int STOVE_AREA_MIN_Y = 3489;
    private static final String COOK_WIDGET_TEXT = "How many would you like to cook?";

    public static String status = "Idle";

    private static long startTimeMs;
    private F2pCookerFood selectedFood;
    private boolean useAntiban;

    public boolean run(F2pCookerConfig config) {
        status = "Starting";
        startTimeMs = System.currentTimeMillis();
        selectedFood = null;
        useAntiban = config.enableAntiban();

        Rs2Antiban.resetAntibanSettings();
        if (useAntiban) {
            Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
            Rs2AntibanSettings.dynamicActivity = true;
            Rs2AntibanSettings.dynamicIntensity = true;
            Rs2AntibanSettings.actionCooldownChance = 0.1;
            Rs2AntibanSettings.microBreakChance = 0.01;
            Rs2AntibanSettings.microBreakDurationLow = 0;
            Rs2AntibanSettings.microBreakDurationHigh = 3;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) {
                    return;
                }

                if (useAntiban && Rs2AntibanSettings.actionCooldownActive) {
                    return;
                }

                if (Rs2Player.isMoving()) {
                    return;
                }

                if (Rs2Widget.findWidget(COOK_WIDGET_TEXT, null, false) != null) {
                    status = "Confirming cook-all";
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                    sleep(300, 700);
                    return;
                }

                if (Rs2Inventory.isFull() && !hasAnyRawFood()) {
                    status = "Banking";
                    bankForRawFood(config);
                    return;
                }

                if (!hasAnyRawFood()) {
                    status = "Getting raw food";
                    bankForRawFood(config);
                    return;
                }

                if (!isNearStove()) {
                    status = "Walking to stove area";
                    Rs2Walker.walkTo(EDGEVILLE_STOVE_POINT, 4);
                    return;
                }

                if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                    return;
                }

                status = "Cooking " + getCurrentFood().getName();
                cookInventory();
            } catch (Exception ex) {
                log.error("F2P Cooker error", ex);
            }
        }, 0, 700, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        status = "Stopped";
        Rs2Antiban.resetAntibanSettings();
    }

    public static Duration getRuntime() {
        if (startTimeMs == 0) {
            return Duration.ZERO;
        }
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return Duration.ofMillis(elapsed);
    }

    private void bankForRawFood(F2pCookerConfig config) {
        if (!Rs2Bank.walkToBankAndUseBank()) {
            status = "Bank unavailable";
            return;
        }

        Rs2Bank.depositAll();

        F2pCookerFood foodToWithdraw = determineFoodToCook(config);
        if (foodToWithdraw == null) {
            status = "No F2P raw fish in bank";
            Microbot.showMessage("F2P Cooker: no suitable raw fish found in bank for your selected mode.");
            shutdown();
            return;
        }

        selectedFood = foodToWithdraw;
        Rs2Bank.withdrawAll(selectedFood.getRawItemId());
        sleepUntil(() -> Rs2Inventory.hasItem(selectedFood.getRawItemId()), 2500);
        Rs2Bank.closeBank();
    }

    private void cookInventory() {
        GameObject stove = findEdgevilleStove();

        if (stove == null) {
            status = "Stove not found in area";
            return;
        }

        status = "Using stove (Cook)";
        if (Rs2GameObject.interact(stove, "Cook")) {
            sleepUntil(() -> Rs2Widget.findWidget(COOK_WIDGET_TEXT, null, false) != null || Rs2Player.isAnimating(), 3000);
            if (Rs2Widget.findWidget(COOK_WIDGET_TEXT, null, false) != null) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            }
            Rs2Player.waitForXpDrop(Skill.COOKING, 4000);
            if (useAntiban) {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        }
    }

    private GameObject findEdgevilleStove() {
        Optional<GameObject> stove = Rs2GameObject.getGameObjects().stream()
                .filter(gameObject -> gameObject != null && gameObject.getId() == EDGEVILLE_STOVE_ID)
                .filter(gameObject -> isInStoveSearchArea(gameObject.getWorldLocation()))
                .min(Comparator.comparingInt(gameObject -> Rs2Player.distanceTo(gameObject.getWorldLocation())));
        return stove.orElse(null);
    }

    private boolean isInStoveSearchArea(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return false;
        }
        return worldPoint.getX() >= STOVE_AREA_MIN_X
                && worldPoint.getX() <= STOVE_AREA_MAX_X
                && worldPoint.getY() >= STOVE_AREA_MIN_Y
                && worldPoint.getY() <= STOVE_AREA_MAX_Y
                && worldPoint.getPlane() == EDGEVILLE_STOVE_POINT.getPlane();
    }

    private boolean isNearStove() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return isInStoveSearchArea(playerLocation);
    }

    private F2pCookerFood getCurrentFood() {
        if (selectedFood != null && Rs2Inventory.hasItem(selectedFood.getRawItemId())) {
            return selectedFood;
        }

        return F2pCookerFood.getLevelOrdered().stream()
                .filter(food -> Rs2Inventory.hasItem(food.getRawItemId()))
                .max(Comparator.comparingInt(F2pCookerFood::getRequiredLevel))
                .orElse(F2pCookerFood.SHRIMP);
    }

    private boolean hasAnyRawFood() {
        return F2pCookerFood.getLevelOrdered().stream().anyMatch(food -> Rs2Inventory.hasItem(food.getRawItemId()));
    }

    private F2pCookerFood determineFoodToCook(F2pCookerConfig config) {
        if (!config.useBestFoodForLevel()) {
            return Rs2Bank.hasItem(config.fallbackFood().getRawItemId()) ? config.fallbackFood() : null;
        }

        int cookingLevel = Microbot.getClientThread().invoke(() -> Microbot.getClient().getRealSkillLevel(Skill.COOKING));
        return F2pCookerFood.getLevelOrdered().stream()
                .filter(food -> cookingLevel >= food.getRequiredLevel())
                .filter(food -> Rs2Bank.hasItem(food.getRawItemId()))
                .max(Comparator.comparingInt(F2pCookerFood::getRequiredLevel))
                .orElse(null);
    }
}
