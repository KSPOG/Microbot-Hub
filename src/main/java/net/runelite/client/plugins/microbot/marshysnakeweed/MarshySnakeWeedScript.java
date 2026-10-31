package net.runelite.client.plugins.microbot.marshysnakeweed;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MarshySnakeWeedScript extends Script {
    private static final int GRIMY_SNAKE_WEED_ID = ItemID.UNIDENTIFIED_SNAKE_WEED;
    private static final int MARSHY_JUNGLE_VINE_ID = ObjectID.SNAKE_VINE_FULL;

    private static final String TAI_BWO_WANNAI_TELEPORT_ITEM = "Tai Bwo Wannai Teleport";
    private static final String TAI_BWO_WANNAI_TELEPORT_ACTION = "Read";
    private static final String VARROCK_TELEPORT_ITEM = "Varrock Teleport";
    private static final String VARROCK_TELEPORT_ACTION = "Break";

    // Random delay applied every time the script is about to press a teleport item,
    // so it doesn't click it the instant it decides to teleport.
    private static final int TELEPORT_PRESS_DELAY_MIN_MS = 300;
    private static final int TELEPORT_PRESS_DELAY_MAX_MS = 1000;

    /**
     * Known marshy jungle vine used by Jungle Potion south of Tai Bwo Wannai.
     */
    private static final WorldPoint VINE_AREA_CENTER = new WorldPoint(2763, 3044, 0);

    private MarshySnakeWeedConfig config;

    @Getter
    private MarshySnakeWeedState state = MarshySnakeWeedState.STARTING;

    @Getter
    private int bankedThisRun = 0;

    @Getter
    private String status = "Starting";

    private long lastClickMs = 0L;
    private long lastWalkMs = 0L;
    private long nextSearchWaitMs = 1800L;

    private boolean teleportedToVinesThisTrip = false;
    private boolean teleportedToBankThisTrip = false;

    public boolean run(MarshySnakeWeedConfig config) {
        this.config = config;
        this.state = MarshySnakeWeedState.STARTING;
        this.status = "Starting";
        this.bankedThisRun = 0;
        this.lastClickMs = 0L;
        this.lastWalkMs = 0L;
        this.nextSearchWaitMs = 1800L;
        this.teleportedToVinesThisTrip = false;
        this.teleportedToBankThisTrip = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop() {
        try {
            if (!super.run() || !Microbot.isLoggedIn()) {
                return;
            }

            state = determineState();

            switch (state) {
                case WALKING_TO_VINES:
                    walkToVines();
                    break;
                case SEARCHING_VINES:
                    searchVines();
                    break;
                case BANKING:
                    bankInventory();
                    break;
                case ERROR_RECOVERY:
                    recover();
                    break;
                case STARTING:
                default:
                    status = "Starting";
                    break;
            }
        } catch (Exception ex) {
            log.error("Marshy Snake Weed loop failed", ex);
            state = MarshySnakeWeedState.ERROR_RECOVERY;
            status = "Error recovery";
        }
    }

    private MarshySnakeWeedState determineState() {
        int snakeWeedCount = getSnakeWeedCount();

        if (Rs2Inventory.emptySlotCount() == 0 || snakeWeedCount >= config.bankAtSnakeWeedCount()) {
            return MarshySnakeWeedState.BANKING;
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return MarshySnakeWeedState.SEARCHING_VINES;
        }

        Rs2TileObjectModel vine = findNearestVine();
        if (vine == null && config.walkToVineArea() && !isNearVineArea()) {
            return MarshySnakeWeedState.WALKING_TO_VINES;
        }

        return MarshySnakeWeedState.SEARCHING_VINES;
    }

    private void walkToVines() {
        if (Rs2Player.isMoving()) {
            status = "Walking to vines";
            return;
        }

        if (config.useTaiBwoWannaiTeleport() && !teleportedToVinesThisTrip) {
            teleportedToVinesThisTrip = true;
            status = "Using Tai Bwo Wannai Teleport";

            if (useTeleportItem(TAI_BWO_WANNAI_TELEPORT_ITEM, TAI_BWO_WANNAI_TELEPORT_ACTION)) {
                lastWalkMs = System.currentTimeMillis();
                return;
            }
        }

        if (System.currentTimeMillis() - lastWalkMs < 1200) {
            return;
        }

        status = "Walking to marshy jungle vines";
        lastWalkMs = System.currentTimeMillis();
        humanDelay(80, 260);
        Rs2Walker.walkTo(VINE_AREA_CENTER);
    }

    private void searchVines() {
        if (Rs2Player.isMoving()) {
            status = "Moving";
            return;
        }

        if (System.currentTimeMillis() - lastClickMs < nextSearchWaitMs) {
            return;
        }

        Rs2TileObjectModel vine = findNearestVine();
        if (vine == null) {
            status = "No visible full vine";

            if (config.walkToVineArea() && !isNearVineArea()) {
                walkToVines();
            }
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint vineLocation = vine.getWorldLocation();
        if (player == null || vineLocation == null) {
            return;
        }

        if (player.distanceTo(vineLocation) > 5) {
            status = "Walking closer to vine";
            if (System.currentTimeMillis() - lastWalkMs > 1200) {
                lastWalkMs = System.currentTimeMillis();
                humanDelay(80, 260);
                Rs2Walker.walkTo(vineLocation);
            }
            return;
        }

        if (shouldSimulateMissClick()) {
            status = "Repositioning mouse";
            lastClickMs = System.currentTimeMillis();
            nextSearchWaitMs = ThreadLocalRandom.current().nextInt(250, 700);
            humanDelay(120, 400);
            return;
        }

        int beforeCount = getSnakeWeedCount();
        status = "Searching marshy jungle vine";
        lastClickMs = System.currentTimeMillis();
        nextSearchWaitMs = rollSearchWaitMs();

        humanDelay(90, 320);

        if (vine.click("Search")) {
            sleepUntil(() -> getSnakeWeedCount() > beforeCount || Rs2Inventory.emptySlotCount() == 0, config.searchWaitMsMax());
            Rs2Inventory.waitForInventoryChanges(config.searchWaitMsMax());
            maybeMicroBreak();
        }
    }

    private void bankInventory() {
        int snakeWeedBeforeBank = getSnakeWeedCount();
        status = "Banking";

        if (config.useVarrockTeleport() && !teleportedToBankThisTrip) {
            teleportedToBankThisTrip = true;
            status = "Using Varrock Teleport";
            useTeleportItem(VARROCK_TELEPORT_ITEM, VARROCK_TELEPORT_ACTION);
        }

        if (!Rs2Bank.walkToBankAndUseBank()) {
            return;
        }

        if (Rs2Inventory.emptySlotCount() == 28) {
            Rs2Bank.closeBank();
            teleportedToBankThisTrip = false;
            teleportedToVinesThisTrip = false;
            return;
        }

        if (shouldSimulateMissClick()) {
            status = "Repositioning mouse at bank";
            humanDelay(150, 500);
            return;
        }

        humanDelay(100, 350);

        String[] keepItems = parseKeepItems(config.itemsToKeep());
        boolean deposited;
        if (keepItems.length == 0) {
            deposited = Rs2Bank.depositAll();
        } else {
            deposited = Rs2Bank.depositAllExcept(keepItems);
        }

        if (deposited) {
            Rs2Inventory.waitForInventoryChanges(1800);
            sleepUntil(() -> getSnakeWeedCount() == 0 || Rs2Inventory.emptySlotCount() > 0, 5000);

            if (snakeWeedBeforeBank > 0 && getSnakeWeedCount() == 0) {
                bankedThisRun += snakeWeedBeforeBank;
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        // Reset teleport flags so the next bank trip and the next walk back to the
        // vines can use a teleport again.
        teleportedToBankThisTrip = false;
        teleportedToVinesThisTrip = false;
    }

    private void recover() {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }

        lastClickMs = 0L;
        lastWalkMs = 0L;
        teleportedToVinesThisTrip = false;
        teleportedToBankThisTrip = false;
        status = "Recovered";
    }

    private Rs2TileObjectModel findNearestVine() {
        try {
            return Microbot.getRs2TileObjectCache()
                    .query()
                    .withId(MARSHY_JUNGLE_VINE_ID)
                    .within(config.vineSearchRadius())
                    .nearestOnClientThread();
        } catch (RuntimeException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("interrupted waiting for client thread")) {
                Thread.currentThread().interrupt();
            } else {
                log.debug("Could not query marshy jungle vine: {}", ex.getMessage());
            }
            return null;
        }
    }

    private boolean isNearVineArea() {
        WorldPoint player = Rs2Player.getWorldLocation();
        return player != null && player.distanceTo(VINE_AREA_CENTER) <= Math.max(8, config.vineSearchRadius());
    }

    private int getSnakeWeedCount() {
        return Rs2Inventory.count(GRIMY_SNAKE_WEED_ID);
    }

    private String[] parseKeepItems(String configuredItems) {
        if (configuredItems == null || configuredItems.trim().isEmpty()) {
            return new String[0];
        }

        return Arrays.stream(configuredItems.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Uses one of the given item from the inventory (e.g. a teleport scroll or tablet).
     * Safe to call even if the item isn't carried: it simply does nothing and returns false,
     * so the script falls back to walking.
     */
    private boolean useTeleportItem(String itemName, String action) {
        if (!Rs2Inventory.hasItem(itemName)) {
            log.debug("{} not found in inventory, falling back to walking.", itemName);
            return false;
        }

        teleportPressDelay();

        boolean used = Rs2Inventory.interact(itemName, action);
        if (used) {
            sleepUntil(() -> !Rs2Player.isAnimating(), 3000);
            sleepUntil(() -> !Rs2Player.isMoving(), 5000);
            humanDelay(200, 500);
        }

        return used;
    }

    // ----------------------------------------------------------------------
    // Anti-ban helpers
    // ----------------------------------------------------------------------

    /**
     * Random pause (300-1000ms) right before pressing a teleport item, every single time,
     * regardless of the anti-ban toggle, so the teleport is never pressed instantly.
     */
    private void teleportPressDelay() {
        int delay = ThreadLocalRandom.current().nextInt(TELEPORT_PRESS_DELAY_MIN_MS, TELEPORT_PRESS_DELAY_MAX_MS + 1);
        sleep(delay);
    }

    /**
     * Picks a randomized wait between searches instead of always using a single fixed value.
     */
    private long rollSearchWaitMs() {
        int min = Math.min(config.searchWaitMsMin(), config.searchWaitMsMax());
        int max = Math.max(config.searchWaitMsMin(), config.searchWaitMsMax());

        if (min == max) {
            return min;
        }

        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Small randomized delay applied before clicks so actions don't fire instantly, robotically.
     */
    private void humanDelay(int minMs, int maxMs) {
        if (config == null || !config.enableAntiban() || !config.humanizedClicks()) {
            return;
        }

        int delay = minMs >= maxMs ? minMs : ThreadLocalRandom.current().nextInt(minMs, maxMs + 1);
        if (delay > 0) {
            sleep(delay);
        }
    }

    /**
     * Rolls the configured miss-click chance. When true, the caller should skip the real
     * action for this tick to simulate a missed/failed click instead of always succeeding.
     */
    private boolean shouldSimulateMissClick() {
        if (config == null || !config.enableAntiban()) {
            return false;
        }

        return ThreadLocalRandom.current().nextInt(100) < config.missClickChance();
    }

    /**
     * Occasionally takes a short randomized break after a successful action.
     */
    private void maybeMicroBreak() {
        if (config == null || !config.enableAntiban()) {
            return;
        }

        if (ThreadLocalRandom.current().nextInt(100) >= config.microBreakChance()) {
            return;
        }

        int min = Math.min(config.microBreakMinMs(), config.microBreakMaxMs());
        int max = Math.max(config.microBreakMinMs(), config.microBreakMaxMs());
        int breakMs = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);

        status = "Taking a short break";
        sleep(breakMs);
    }

    @Override
    public void shutdown() {
        super.shutdown();

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }

        state = MarshySnakeWeedState.STARTING;
        status = "Stopped";
        lastClickMs = 0L;
        lastWalkMs = 0L;
        teleportedToVinesThisTrip = false;
        teleportedToBankThisTrip = false;
    }
}
