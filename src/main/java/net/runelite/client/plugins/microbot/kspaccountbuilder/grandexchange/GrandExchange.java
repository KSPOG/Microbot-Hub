package net.runelite.client.plugins.microbot.kspaccountbuilder.grandexchange;

import lombok.Getter;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.ge.sell.Sell;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Getter
public class GrandExchange {
    private String status = "Idle";

    public boolean sellAndBuyIfNeeded(
            List<Integer> idsToSell,
            Supplier<List<String>> upgradesSupplier,
            Function<Integer, String> itemNameResolver
    ) {
        if (!Rs2GrandExchange.walkToGrandExchange() || !Rs2GrandExchange.openExchange()) {
            status = "Unable to access GE";
            return false;
        }

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            status = "Unable to open GE bank";
            return false;
        }

        Rs2Bank.depositEquipment();
        Global.sleep(200);
        Rs2Bank.depositAll();

        Rs2Bank.setWithdrawAsItem();
        for (int sellId : idsToSell) {
            int bankCount = Rs2Bank.count(sellId);
            if (bankCount <= 0) {
                continue;
            }

            Rs2Bank.withdrawX(sellId, bankCount);
            Global.sleep(120);
        }
        Rs2Bank.closeBank();

        if (!Rs2GrandExchange.openExchange()) {
            status = "Unable to open GE after bank";
            return false;
        }

        status = "Selling configured loot";
        boolean hadItemsToSell = !Rs2Inventory.isEmpty();
        int totalOffersPlaced = 0;

        List<Integer> defaultSellIds = getDefaultSellItemIds();
        int defaultOffersPlaced = sellAllWithSlotWaiting(defaultSellIds, itemNameResolver);
        if (defaultOffersPlaced < 0) {
            status = "Timed out waiting for sell offers";
            Rs2GrandExchange.closeExchange();
            return false;
        }

        totalOffersPlaced += defaultOffersPlaced;

        List<Integer> upgradeSellIds = new ArrayList<>(idsToSell);
        upgradeSellIds.removeAll(defaultSellIds);
        int upgradeOffersPlaced = sellAllWithSlotWaiting(upgradeSellIds, itemNameResolver);
        if (upgradeOffersPlaced < 0) {
            status = "Timed out waiting for sell offers";
            Rs2GrandExchange.closeExchange();
            return false;
        }

        totalOffersPlaced += upgradeOffersPlaced;

        if (hadItemsToSell && totalOffersPlaced <= 0) {
            status = "No sell offers were placed";
            Rs2GrandExchange.closeExchange();
            return false;
        }

        if (!emptyInventoryBeforeBuying()) {
            Rs2GrandExchange.closeExchange();
            return false;
        }

        status = "Buying upgrades";
        if (!buyUpgradesWithSlotWaiting(upgradesSupplier.get())) {
            status = "Timed out waiting for buy offers";
            Rs2GrandExchange.closeExchange();
            return false;
        }

        Rs2GrandExchange.collectAllToBank();
        Rs2GrandExchange.closeExchange();
        status = "Finished GE cycle";
        return true;
    }

    public List<Integer> getDefaultSellItemIds() {
        List<Integer> idsToSell = new ArrayList<>();
        for (int sellId : Sell.DEFAULT_SELL) {
            if (!idsToSell.contains(sellId)) {
                idsToSell.add(sellId);
            }
        }
        return idsToSell;
    }

    private int sellAllWithSlotWaiting(List<Integer> itemIds, Function<Integer, String> itemNameResolver) {
        int totalOffersPlaced = 0;
        while (hasSellableInventory(itemIds, itemNameResolver)) {
            int availableSlotsBeforeSelling = Rs2GrandExchange.getAvailableSlotsCount();
            if (availableSlotsBeforeSelling <= 0) {
                if (!waitUntilGeSlotAvailable()) {
                    return -1;
                }
                continue;
            }

            int sellOffersPlaced = placeSellOffers(itemIds, availableSlotsBeforeSelling, itemNameResolver);
            if (sellOffersPlaced <= 0) {
                return -1;
            }

            totalOffersPlaced += sellOffersPlaced;

            if (!waitForSellOffersToComplete(availableSlotsBeforeSelling, sellOffersPlaced)) {
                return -1;
            }
        }

        return totalOffersPlaced;
    }

    private boolean hasSellableInventory(List<Integer> itemIds, Function<Integer, String> itemNameResolver) {
        for (int sellId : itemIds) {
            String itemName = itemNameResolver.apply(sellId);
            if (itemName != null && !itemName.isBlank() && Rs2Inventory.count(itemName) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean emptyInventoryBeforeBuying() {
        if (Rs2Inventory.isEmpty()) {
            return true;
        }

        status = "Emptying inventory before buying upgrades";
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            status = "Unable to open GE bank to empty inventory";
            return false;
        }

        Rs2Bank.depositAll();
        Global.sleep(200);
        Rs2Bank.closeBank();
        if (!Rs2Inventory.isEmpty()) {
            return false;
        }

        status = "Re-opening GE for upgrades";
        return Rs2GrandExchange.openExchange();
    }

    private boolean buyUpgradesWithSlotWaiting(List<String> upgradeNames) {
        List<String> remainingUpgrades = new ArrayList<>(upgradeNames);
        while (!remainingUpgrades.isEmpty()) {
            int availableSlotsBeforeBuying = Rs2GrandExchange.getAvailableSlotsCount();
            if (availableSlotsBeforeBuying <= 0) {
                if (!waitUntilGeSlotAvailable()) {
                    return false;
                }
                continue;
            }

            int buyOffersPlaced = 0;
            int slotsToUse = availableSlotsBeforeBuying;
            while (slotsToUse > 0 && !remainingUpgrades.isEmpty()) {
                String itemName = remainingUpgrades.remove(0);
                if (placeOffer(itemName, GrandExchangeAction.BUY, 1)) {
                    buyOffersPlaced++;
                    slotsToUse--;
                }
                Global.sleep(350);
            }

            if (buyOffersPlaced <= 0) {
                return false;
            }

            if (!waitForBuyOffersToComplete(availableSlotsBeforeBuying, buyOffersPlaced)) {
                return false;
            }
        }

        return true;
    }

    private boolean waitUntilGeSlotAvailable() {
        long timeoutAt = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < timeoutAt) {
            if (Rs2GrandExchange.hasSoldOffer() || Rs2GrandExchange.hasBoughtOffer()) {
                Rs2GrandExchange.collectAllToBank();
            }

            if (Rs2GrandExchange.getAvailableSlotsCount() > 0) {
                return true;
            }

            Global.sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0
                    || Rs2GrandExchange.hasSoldOffer()
                    || Rs2GrandExchange.hasBoughtOffer(), 2_000);
        }

        Rs2GrandExchange.collectAllToBank();
        return Rs2GrandExchange.getAvailableSlotsCount() > 0;
    }

    private boolean placeOffer(String itemName, GrandExchangeAction action, int quantity) {
        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(action)
                .itemName(itemName)
                .quantity(Math.max(1, quantity))
                .percent(8)
                .closeAfterCompletion(false)
                .build();
        return Rs2GrandExchange.processOffer(request);
    }

    private int placeSellOffers(List<Integer> itemIds, int maxOffers, Function<Integer, String> itemNameResolver) {
        int offersPlaced = 0;
        for (int sellId : itemIds) {
            if (offersPlaced >= maxOffers) {
                break;
            }
            String itemName = itemNameResolver.apply(sellId);
            if (itemName == null || itemName.isBlank()) {
                continue;
            }

            int invCount = Rs2Inventory.count(itemName);
            if (invCount <= 0) {
                continue;
            }

            if (placeOffer(itemName, GrandExchangeAction.SELL, invCount)) {
                offersPlaced++;
            }
            Global.sleep(350);
        }
        return offersPlaced;
    }

    private boolean waitForSellOffersToComplete(int availableSlotsBeforeSelling, int sellOffersPlaced) {
        if (sellOffersPlaced <= 0) {
            return true;
        }

        long timeoutAt = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < timeoutAt) {
            if (Rs2GrandExchange.hasSoldOffer()) {
                Rs2GrandExchange.collectAllToBank();
            }

            if (Rs2GrandExchange.getAvailableSlotsCount() >= availableSlotsBeforeSelling) {
                Rs2GrandExchange.collectAllToBank();
                return true;
            }

            Global.sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() >= availableSlotsBeforeSelling
                    || Rs2GrandExchange.hasSoldOffer(), 2_000);
        }

        Rs2GrandExchange.collectAllToBank();
        return false;
    }

    private boolean waitForBuyOffersToComplete(int availableSlotsBeforeBuying, int buyOffersPlaced) {
        if (buyOffersPlaced <= 0) {
            return true;
        }

        long timeoutAt = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < timeoutAt) {
            if (Rs2GrandExchange.hasBoughtOffer()) {
                Rs2GrandExchange.collectAllToBank();
            }

            if (Rs2GrandExchange.getAvailableSlotsCount() >= availableSlotsBeforeBuying) {
                Rs2GrandExchange.collectAllToBank();
                return true;
            }

            Global.sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() >= availableSlotsBeforeBuying
                    || Rs2GrandExchange.hasBoughtOffer(), 2_000);
        }

        Rs2GrandExchange.collectAllToBank();
        return false;
    }
}
