package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.ge.buy;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Grand Exchange buying helpers for KSPAccountBuilder.
 */
@Slf4j
public final class Buy {

    private static final int BUY_WAIT_TIMEOUT_MS = 20000;
    private static final List<String> AXE_NAMES = List.of(
            "Bronze axe",
            "Steel axe",
            "Black axe",
            "Mithril axe",
            "Adamant axe",
            "Rune axe"
    );

    private Buy() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean buyMissingAxes() {
        if (!refreshBankItems()) {
            return false;
        }

        List<String> missingAxes = new ArrayList<>();
        for (String axe : AXE_NAMES) {
            if (!hasAxe(axe)) {
                missingAxes.add(axe);
            }
        }

        if (missingAxes.isEmpty()) {
            return true;
        }

        if (!Rs2GrandExchange.walkToGrandExchange() || !Rs2GrandExchange.openExchange()) {
            return false;
        }

        sleepUntil(Rs2GrandExchange::isOpen, 7000);
        if (!Rs2GrandExchange.isOpen()) {
            return false;
        }

        for (String axe : missingAxes) {
            if (hasAxe(axe)) {
                continue;
            }

            if (!ensureExchangeSlotAvailable()) {
                log.warn("Buy: No GE slot available for {}.", axe);
                continue;
            }

            if (!placeBuyOffer(axe)) {
                log.warn("Buy: Failed placing buy offer for {}.", axe);
                continue;
            }

            boolean offerFinished = sleepUntil(Rs2GrandExchange::hasFinishedBuyingOffers, BUY_WAIT_TIMEOUT_MS);
            if (!offerFinished) {
                log.warn("Buy: Timed out waiting for buy offer to finish for {}.", axe);
            }

            collectOffersToBank();
        }

        collectOffersToBank();
        Rs2GrandExchange.closeExchange();
        return true;
    }


    public static boolean buyItemToBank(String itemName, int quantity) {
        if (itemName == null || itemName.isBlank() || quantity <= 0) {
            return false;
        }

        if (!Rs2GrandExchange.walkToGrandExchange() || !Rs2GrandExchange.openExchange()) {
            return false;
        }

        sleepUntil(Rs2GrandExchange::isOpen, 7000);
        if (!Rs2GrandExchange.isOpen()) {
            return false;
        }

        if (!ensureExchangeSlotAvailable()) {
            return false;
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.BUY)
                .itemName(itemName)
                .quantity(quantity)
                .percent(8)
                .closeAfterCompletion(false)
                .build();

        boolean offered = Rs2GrandExchange.processOffer(request);
        if (!offered) {
            return false;
        }

        sleepUntil(() -> Rs2GrandExchange.hasBoughtOffer() || !Rs2GrandExchange.isOpen(), BUY_WAIT_TIMEOUT_MS);
        collectOffersToBank();
        Rs2GrandExchange.closeExchange();
        return true;
    }

    private static boolean refreshBankItems() {
        if (Rs2Bank.isOpen()) {
            return true;
        }

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        sleepUntil(() -> !Rs2Bank.bankItems().isEmpty(), 5000);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        return true;
    }

    private static boolean hasAxe(String axeName) {
        return Rs2Inventory.hasItem(axeName, true) || countBankItem(axeName) > 0;
    }

    private static int countBankItem(String itemName) {
        return Rs2Bank.bankItems().stream()
                .filter(item -> item.getName() != null && item.getName().equalsIgnoreCase(itemName))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private static boolean ensureExchangeSlotAvailable() {
        if (Rs2GrandExchange.getAvailableSlotsCount() > 0) {
            return true;
        }

        collectOffersToBank();
        sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0, 5000);
        return Rs2GrandExchange.getAvailableSlotsCount() > 0;
    }

    private static void collectOffersToBank() {
        if (!Rs2GrandExchange.hasBoughtOffer() && !Rs2GrandExchange.hasSoldOffer()) {
            return;
        }

        Rs2GrandExchange.collectAllToBank();
        sleepUntil(() -> !Rs2GrandExchange.hasBoughtOffer() && !Rs2GrandExchange.hasSoldOffer(), 5000);
    }

    private static boolean placeBuyOffer(String itemName) {
        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.BUY)
                .itemName(itemName)
                .quantity(1)
                .percent(8)
                .closeAfterCompletion(false)
                .build();

        boolean offered = Rs2GrandExchange.processOffer(request);
        sleepUntil(Rs2GrandExchange::isOpen, 3000);
        return offered;
    }
}
