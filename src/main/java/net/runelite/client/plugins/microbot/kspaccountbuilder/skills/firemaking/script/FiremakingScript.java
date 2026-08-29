package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.firemaking.script;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.firemaking.needed.Needed;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.ge.buy.Buy;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import java.awt.event.KeyEvent;

@Slf4j
public class FiremakingScript {
    private static final int CAMPFIRE_SEARCH_RADIUS = 20;
    private static final int CAMPFIRE_LIGHT_WAIT_TIMEOUT_MS = 4_000;
    private static final int CAMPFIRE_ACTION_SETTLE_TIMEOUT_MS = 6_000;
    private static final String[] PRODUCT_SELECTION_PROMPTS = {
            "Product selection",
            "How many would you like to burn?",
            "How many would you like to make?"
    };

    private String status = "Idle";

    public void initialize() {
        status = "Initializing firemaking";
    }

    public void shutdown() {
        status = "Firemaking stopped";
    }

    public String getStatus() {
        return status;
    }

    public boolean hasRequiredSuppliesInInventory() {
        if (!Microbot.isLoggedIn()) {
            return false;
        }

        int bestLogId = resolveBestLogForCurrentLevel();
        return hasRequiredSupplies(bestLogId);
    }

    public void execute() {
        if (!Microbot.isLoggedIn()) {
            status = "Waiting for login";
            return;
        }

        int bestLogId = resolveBestLogForCurrentLevel();

        if (handleProductSelectionDialogue()) {
            return;
        }

        if (handleBurnSelectionWidget(bestLogId)) {
            return;
        }

        if (!hasRequiredSupplies(bestLogId)) {
            restockSupplies(bestLogId);
            return;
        }

        if (tendActiveForestersCampfire(bestLogId)) {
            return;
        }

        if (lightForestersCampfire(bestLogId)) {
            return;
        }

        status = "Waiting for active Forester's Campfire at Grand Exchange";
        Rs2GrandExchange.walkToGrandExchange();
    }

    private int resolveBestLogForCurrentLevel() {
        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        return Needed.getBestLogsForLevel(firemakingLevel);
    }


    private boolean handleProductSelectionDialogue() {
        if (!isProductSelectionDialogueOpen()) {
            return false;
        }

        status = "Confirming product selection";

        // Per request: use keyboard confirmation on selection dialogue.
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

        Global.sleepUntil(() -> !isProductSelectionDialogueOpen()
                || Rs2Player.isAnimating()
                || Rs2Player.isInteracting(), 2_500);
        return true;
    }

    private boolean isProductSelectionDialogueOpen() {
        for (String prompt : PRODUCT_SELECTION_PROMPTS) {
            if (Rs2Widget.hasWidget(prompt)) {
                return true;
            }
        }

        return Rs2Widget.isProductionWidgetOpen();
    }

    private boolean handleBurnSelectionWidget(int ignoredBestLogId) {
        if (!Rs2Widget.hasWidget("What would you like to burn?")) {
            return false;
        }

        status = "Confirming burn selection";

        // Per request: use keyboard confirmation instead of clicking selection options.
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

        Global.sleepUntil(() -> !Rs2Widget.hasWidget("What would you like to burn?")
                || Rs2Player.isAnimating()
                || Rs2Player.isInteracting(), 3_000);
        return true;
    }

    private boolean hasRequiredSupplies(int bestLogId) {
        return Rs2Inventory.hasItem(Needed.TINDERBOX)
                && Rs2Inventory.hasItem(bestLogId)
                && Rs2Inventory.count(bestLogId) > 0;
    }

    private void restockSupplies(int bestLogId) {
        if (!withdrawRequiredSuppliesFromGrandExchangeBank(bestLogId)) {
            status = "Buying firemaking supplies";

            if (!Rs2Inventory.hasItem(Needed.TINDERBOX)) {
                Buy.buyItemToBank("Tinderbox", Needed.TINDERBOX_COUNT);
            }

            String logName = getLogName(bestLogId);
            if (!logName.isEmpty()) {
                Buy.buyItemToBank(logName, Needed.LOG_COUNT);
            }

            withdrawRequiredSuppliesFromGrandExchangeBank(bestLogId);
        }
    }

    private boolean withdrawRequiredSuppliesFromGrandExchangeBank(int bestLogId) {
        status = "Walking to Grand Exchange bank";
        if (!Rs2GrandExchange.walkToGrandExchange()) {
            return false;
        }

        status = "Opening Grand Exchange bank";
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        status = "Withdrawing tinderbox and best logs";
        Rs2Bank.depositAll();
        Global.sleep(250);

        Rs2Bank.withdrawX(Needed.TINDERBOX, Needed.TINDERBOX_COUNT);
        Global.sleepUntil(() -> Rs2Inventory.hasItem(Needed.TINDERBOX), 3_000);

        Rs2Bank.withdrawX(bestLogId, Needed.LOG_COUNT);
        Global.sleepUntil(() -> Rs2Inventory.hasItem(bestLogId), 3_000);

        boolean hasSupplies = Rs2Inventory.hasItem(Needed.TINDERBOX) && Rs2Inventory.hasItem(bestLogId);
        if (!hasSupplies) {
            status = "Missing required firemaking supplies in bank";
            log.debug("Missing required items. Tinderbox present: {}, log {} present: {}",
                    Rs2Inventory.hasItem(Needed.TINDERBOX),
                    bestLogId,
                    Rs2Inventory.hasItem(bestLogId));
        }

        Rs2Bank.closeBank();
        return hasSupplies;
    }

    private String getLogName(int logId) {
        if (logId == Needed.WILLOW_LOGS) {
            return "Willow logs";
        }

        if (logId == Needed.OAK_LOGS) {
            return "Oak logs";
        }

        if (logId == Needed.LOGS) {
            return "Logs";
        }

        return "";
    }

    private boolean tendActiveForestersCampfire(int bestLogId) {
        TileObject campfire = Rs2GameObject.findReachableObject("forester", false, CAMPFIRE_SEARCH_RADIUS, Rs2Player.getWorldLocation());
        if (campfire == null) {
            return false;
        }

        if (Rs2Player.distanceTo(campfire.getWorldLocation()) > 4) {
            status = "Walking to active Forester's Campfire";
            Rs2Walker.walkTo(campfire.getWorldLocation());
            return true;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            status = "Waiting for current action";
            return true;
        }

        status = "Tending active Forester's Campfire";
        boolean tended = Rs2GameObject.interact(campfire, "Tend-to")
                || Rs2GameObject.interact(campfire, "Tend")
                || Rs2Inventory.useItemOnObject(bestLogId, campfire.getId());

        if (tended) {
            Global.sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 2_500);
            Global.sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 8_000);
        }

        return true;
    }

    private boolean lightForestersCampfire(int bestLogId) {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            status = "Waiting to light Forester's Campfire";
            return true;
        }

        if (!Rs2Inventory.hasItem(Needed.TINDERBOX) || !Rs2Inventory.hasItem(bestLogId)) {
            return false;
        }

        status = "Lighting Forester's Campfire";
        boolean lit = Rs2Inventory.combine("Tinderbox", getLogName(bestLogId));
        if (!lit) {
            return false;
        }

        Global.sleepUntil(this::isProductSelectionDialogueOpen, CAMPFIRE_LIGHT_WAIT_TIMEOUT_MS);
        Global.sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), CAMPFIRE_LIGHT_WAIT_TIMEOUT_MS);
        Global.sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), CAMPFIRE_ACTION_SETTLE_TIMEOUT_MS);
        return true;
    }
}
