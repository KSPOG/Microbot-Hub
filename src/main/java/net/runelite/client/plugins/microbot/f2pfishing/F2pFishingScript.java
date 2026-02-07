package net.runelite.client.plugins.microbot.f2pfishing;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class F2pFishingScript extends Script {
    private static final List<String> CONSUMABLE_ITEMS = List.of("Fishing bait", "Feather");

    private static final int COIN_BUFFER = 10000;


    public static String status = "Idle";
    public static String modeLabel = "";
    public static int fishCaught = 0;

    private static long startTimeMs;
    private int lastInventoryCount;
    private F2pFishingFish selectedFish;
    private WorldPoint fishingLocation;
    private String fishAction = "";
    private boolean useAntiban;

    public boolean run(F2pFishingConfig config) {
        startTimeMs = System.currentTimeMillis();
        fishCaught = 0;
        lastInventoryCount = 0;
        status = "Starting";
        modeLabel = config.mode().toString();
        selectedFish = config.fish();
        useAntiban = config.enableAntiban();

        Rs2Antiban.resetAntibanSettings();
        if (useAntiban) {
            Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
            Rs2AntibanSettings.actionCooldownChance = 0.2;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) {
                    return;
                }
                if (useAntiban && Rs2AntibanSettings.actionCooldownActive) {
                    return;
                }

                if (selectedFish != config.fish()) {
                    selectedFish = config.fish();
                    fishAction = "";
                    fishingLocation = null;
                    lastInventoryCount = 0;
                }
                modeLabel = config.mode().toString();
                updateFishCount();

                if (!hasRequiredLevel()) {
                    status = "Fishing level too low";
                    return;
                }

                if (Rs2Inventory.isFull()) {
                    if (config.mode().isBankingMode()) {
                        status = "Banking";
                        bankFish();
                    } else {
                        status = "Dropping";
                        dropFish();
                    }
                    return;
                }

                if (!hasRequiredSupplies(config)) {
                    status = "Getting supplies";
                    handleGettingSupplies(config);
                    return;
                }

                if (isBusyFishing() || Rs2Player.isMoving()) {
                    return;
                }

                if (!isAtFishingLocation()) {
                    status = "Walking to spot";
                    handleTraveling();
                    return;
                }

                status = "Fishing " + selectedFish;
                handleFishing();
            } catch (Exception ex) {
                log.error("F2P Fishing error", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

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

    private void handleTraveling() {
        if (fishingLocation == null) {
            fishingLocation = selectedFish.getClosestLocation(Rs2Player.getWorldLocation());
        }
        if (fishingLocation != null) {
            Rs2Walker.walkTo(fishingLocation);
        }
    }

    private void handleFishing() {
        Rs2NpcModel fishingSpot = findNearestFishingSpot();
        if (fishingSpot == null) {
            sleep(1000, 2000);
            return;
        }
        if (fishAction.isEmpty() || !hasFishingAction(fishingSpot, fishAction)) {
            fishAction = selectPreferredAction(fishingSpot, selectedFish.getActions());
        }
        if (!fishAction.isEmpty() && Rs2Npc.interact(fishingSpot, fishAction)) {
            sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isAnimating(), 2000);
            Rs2Player.waitForXpDrop(Skill.FISHING);
            if (useAntiban) {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        }
    }

    private void bankFish() {
        if (!Rs2Bank.walkToBankAndUseBank()) {
            return;
        }
        Rs2Bank.depositAllExcept(getItemsToKeep().toArray(new String[0]));
        sleepUntil(() -> !Rs2Inventory.isFull(), 2000);
        Rs2Bank.closeBank();
    }

    private void dropFish() {
        Rs2Inventory.dropAll(selectedFish.getItemNames().toArray(new String[0]));
    }

    private void handleGettingSupplies(F2pFishingConfig config) {
        if (!Rs2Bank.walkToBankAndUseBank()) {
            status = "Bank unavailable";
            return;
        }

        Rs2Bank.depositAll();

        Map<String, Integer> missingItems = getMissingItemsFromBank(config);
        if (!missingItems.isEmpty()) {
            Rs2Bank.closeBank();
            buyMissingItems(missingItems);
            return;
        }

        withdrawRequiredItems(config);
        if (!hasRequiredSupplies(config)) {
            status = "Waiting for supplies";
            return;
        }
        Rs2Bank.closeBank();
    }

    private void buyMissingItems(Map<String, Integer> missingItems) {
        if (missingItems.isEmpty()) {
            return;
        }

        int totalCost = estimatePurchaseCost(missingItems);
        ensureCoins(totalCost);

        if (!Rs2GrandExchange.walkToGrandExchange()) {
            status = "Could not reach GE";
            return;
        }
        if (!Rs2GrandExchange.openExchange()) {
            status = "GE unavailable";
            return;
        }

        if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
            if (Rs2GrandExchange.hasSoldOffer() || Rs2GrandExchange.hasBoughtOffer()) {
                Rs2GrandExchange.collectAllToBank();
            } else {
                Rs2GrandExchange.abortAllOffers(true);
            }
        }

        for (Map.Entry<String, Integer> entry : missingItems.entrySet()) {
            int gePrice = Microbot.getRs2ItemManager().getGEPrice(entry.getKey());
            int maxPrice = Math.max(1, (int) (gePrice * 1.1));
            Rs2GrandExchange.buyItem(entry.getKey(), maxPrice, entry.getValue());
            sleep(1200, 1800);
        }

        status = "Waiting for GE buys";
        sleepUntil(Rs2GrandExchange::hasFinishedBuyingOffers, 120000);
        Rs2GrandExchange.collectAllToBank();
        Rs2GrandExchange.closeExchange();
    }

    private void ensureCoins(int totalCost) {
        if (totalCost <= 0) {
            return;
        }
        int currentCoins = Rs2Inventory.itemQuantity(ItemID.COINS_995);
        if (currentCoins >= totalCost) {
            return;
        }
        if (Rs2Bank.walkToBankAndUseBank()) {
            int withdrawAmount = totalCost - currentCoins;
            Rs2Bank.withdrawX(true, ItemID.COINS_995, withdrawAmount);
            sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS_995) >= totalCost, 2000);
            Rs2Bank.closeBank();
        }
    }

    private int estimatePurchaseCost(Map<String, Integer> missingItems) {
        int totalCost = 0;
        for (Map.Entry<String, Integer> entry : missingItems.entrySet()) {
            int price = Microbot.getRs2ItemManager().getGEPrice(entry.getKey());
            int quantity = Math.max(1, entry.getValue());
            totalCost += (int) (price * quantity * 1.1);
        }
        return totalCost;
    }

    private void withdrawRequiredItems(F2pFishingConfig config) {

        if (shouldKeepCoins()) {
            ensureCoins(COIN_BUFFER);
        }

        for (String item : selectedFish.getRequiredItems()) {
            if (isConsumable(item)) {
                int currentAmount = Rs2Inventory.itemQuantity(item);
                if (currentAmount == 0 && Rs2Bank.hasItem(item)) {
                    Rs2Bank.withdrawAll(item);
                    sleepUntil(() -> Rs2Inventory.itemQuantity(item) > 0 || !Rs2Bank.hasItem(item), 2000);
                }
                continue;
            }
            if (requiresInventory(item)) {
                if (Rs2Inventory.hasItem(item)) {
                    continue;
                }
            } else if (hasItemEquippedOrInventory(item)) {
                continue;
            }
            Rs2Bank.withdrawOne(item);
            sleepUntil(() -> Rs2Inventory.hasItem(item), 2000);
        }
    }

    private Map<String, Integer> getMissingItemsFromBank(F2pFishingConfig config) {
        Map<String, Integer> missingItems = new LinkedHashMap<>();
        for (String item : selectedFish.getRequiredItems()) {
            int currentAmount = Rs2Inventory.itemQuantity(item);
            if (isConsumable(item)) {
                boolean hasAny = currentAmount > 0 || Rs2Bank.hasItem(item);
                if (!hasAny) {
                    missingItems.put(item, 1);
                }
                continue;
            }
            if (hasItemEquippedOrInventory(item)) {
                continue;
            }
            if (!Rs2Bank.hasItem(item)) {
                missingItems.put(item, 1);
            }
        }
        return missingItems;
    }

    private boolean isConsumable(String itemName) {
        return CONSUMABLE_ITEMS.contains(itemName);
    }

    private boolean hasRequiredSupplies(F2pFishingConfig config) {

        if (shouldKeepCoins() && Rs2Inventory.itemQuantity(ItemID.COINS_995) < COIN_BUFFER) {
            return false;
        }

        for (String item : selectedFish.getRequiredItems()) {
            if (isConsumable(item)) {
                if (Rs2Inventory.itemQuantity(item) == 0) {
                    return false;
                }
                continue;
            }
            if (requiresInventory(item)) {
                if (!Rs2Inventory.hasItem(item)) {
                    return false;
                }
            } else if (!hasItemEquippedOrInventory(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasItemEquippedOrInventory(String item) {
        return Rs2Inventory.hasItem(item) || Rs2Equipment.isWearing(item);
    }


    private boolean shouldKeepCoins() {
        return selectedFish == F2pFishingFish.TUNA_AND_SWORDFISH;
    }


    private boolean requiresInventory(String item) {
        return "Fishing rod".equalsIgnoreCase(item);
    }

    private boolean isAtFishingLocation() {
        if (fishingLocation == null) {
            return false;
        }
        return Rs2Player.getWorldLocation().distanceTo(fishingLocation) <= 5;
    }

    private boolean hasFishingAction(Rs2NpcModel fishingSpot, String action) {
        return !Rs2Npc.getAvailableAction(fishingSpot, List.of(action)).isEmpty();
    }

    private boolean isBusyFishing() {
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return true;
        }

        AtomicBoolean isInteracting = new AtomicBoolean(false);
        Microbot.getClientThread().invoke(() -> {
            if (Microbot.getClient().getLocalPlayer() != null) {
                isInteracting.set(Microbot.getClient().getLocalPlayer().isInteracting());
            }
        });
        return isInteracting.get();

        return Microbot.getClientThread().invoke(() -> {
            if (Microbot.getClient().getLocalPlayer() == null) {
                return false;
            }
            return Microbot.getClient().getLocalPlayer().isInteracting();
        });

    }

    private String selectPreferredAction(Rs2NpcModel fishingSpot, List<String> actions) {
        for (String action : actions) {
            String available = Rs2Npc.getAvailableAction(fishingSpot, List.of(action));
            if (!available.isEmpty()) {
                return available;
            }
        }
        return Rs2Npc.getAvailableAction(fishingSpot, actions);
    }

    private boolean hasRequiredLevel() {
        int fishingLevel = Microbot.getClient().getRealSkillLevel(Skill.FISHING);
        return fishingLevel >= selectedFish.getLevelRequired();
    }

    private Rs2NpcModel findNearestFishingSpot() {
        int[] spotIds = selectedFish.getFishingSpotIds();
        return Rs2Npc.getNpcs(npc -> Arrays.stream(spotIds).anyMatch(id -> npc.getId() == id))
                .findFirst()
                .orElse(null);
    }

    private void updateFishCount() {
        int currentCount = countRelevantFish();
        int delta = currentCount - lastInventoryCount;
        if (delta > 0) {
            fishCaught += delta;
        }
        lastInventoryCount = currentCount;
    }

    private int countRelevantFish() {
        int count = 0;
        for (String itemName : selectedFish.getItemNames()) {
            count += Rs2Inventory.itemQuantity(itemName);
        }
        return count;
    }

    private List<String> getItemsToKeep() {
        return selectedFish.getRequiredItems();
    }
}
