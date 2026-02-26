package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.firemaking.script;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.firemaking.needed.Needed;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Slf4j
public class FiremakingScript {
    private static final int CAMPFIRE_SEARCH_RADIUS = 20;

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

    public void execute() {
        if (!Microbot.isLoggedIn()) {
            status = "Waiting for login";
            return;
        }

        int bestLogId = resolveBestLogForCurrentLevel();

        if (handleBurnSelectionWidget(bestLogId)) {
            return;
        }

        if (!hasRequiredSupplies(bestLogId)) {
            withdrawRequiredSuppliesFromGrandExchangeBank(bestLogId);
            return;
        }

        if (tendActiveForestersCampfire(bestLogId)) {
            return;
        }

        status = "Waiting for active Forester's Campfire at Grand Exchange";
        Rs2GrandExchange.walkToGrandExchange();
    }

    private int resolveBestLogForCurrentLevel() {
        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        return Needed.getBestLogsForLevel(firemakingLevel);
    }


    private boolean handleBurnSelectionWidget(int bestLogId) {
        if (!Rs2Widget.hasWidget("What would you like to burn?")) {
            return false;
        }

        status = "Selecting burn quantity and logs";

        // New burn interface requires selecting quantity and then selecting the log option.
        Rs2Widget.clickWidget("All");

        String logName = getLogName(bestLogId);
        boolean clickedLog = Rs2Widget.clickWidget(logName);
        if (!clickedLog) {
            // Fallback to generic logs label in case naming differs by client/build.
            Rs2Widget.clickWidget("Logs");
        }

        Global.sleepUntil(() -> !Rs2Widget.hasWidget("What would you like to burn?")
                || Rs2Player.isAnimating()
                || Rs2Player.isInteracting(), 3_000);
        return true;
    }

    private String getLogName(int logId) {
        if (logId == Needed.WILLOW_LOGS) {
            return "Willow logs";
        }

        if (logId == Needed.OAK_LOGS) {
            return "Oak logs";
        }

        return "Logs";
    }

    private boolean hasRequiredSupplies(int bestLogId) {
        return Rs2Inventory.hasItem(Needed.TINDERBOX)
                && Rs2Inventory.hasItem(bestLogId)
                && Rs2Inventory.count(bestLogId) > 0;
    }

    private void withdrawRequiredSuppliesFromGrandExchangeBank(int bestLogId) {
        status = "Walking to Grand Exchange bank";
        if (!Rs2GrandExchange.walkToGrandExchange()) {
            return;
        }

        status = "Opening Grand Exchange bank";
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return;
        }

        status = "Withdrawing tinderbox and best logs";
        Rs2Bank.depositAll();
        Global.sleep(250);

        Rs2Bank.withdrawX(Needed.TINDERBOX, Needed.TINDERBOX_COUNT);
        Global.sleepUntil(() -> Rs2Inventory.hasItem(Needed.TINDERBOX), 3_000);

        Rs2Bank.withdrawX(bestLogId, Needed.LOG_COUNT);
        Global.sleepUntil(() -> Rs2Inventory.hasItem(bestLogId), 3_000);

        if (!Rs2Inventory.hasItem(Needed.TINDERBOX) || !Rs2Inventory.hasItem(bestLogId)) {
            status = "Missing required firemaking supplies in bank";
            log.debug("Missing required items. Tinderbox present: {}, log {} present: {}",
                    Rs2Inventory.hasItem(Needed.TINDERBOX),
                    bestLogId,
                    Rs2Inventory.hasItem(bestLogId));
            return;
        }

        Rs2Bank.closeBank();
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
}
