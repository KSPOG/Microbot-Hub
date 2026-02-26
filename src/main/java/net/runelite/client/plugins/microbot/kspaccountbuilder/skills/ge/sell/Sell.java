package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.ge.sell;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.firemaking.levels.LogLevels;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public final class Sell {
    private static final int SELL_WAIT_TIMEOUT_MS = 20_000;

    private static final String[] SELLABLE_BANK_ITEMS_KEEP_ONE = {
            "Goblin mail",
            "Chef's hat",
            "Air talisman",
            "Earth talisman",
            "Cowhide",
            "Steel longsword",
            "Limpwurt root",
            "Body talisman"
    };

    private static final String NORMAL_LOGS = "Logs";
    private static final String OAK_LOGS = "Oak logs";

    private Sell() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean hasSellableOverflowInBank(int firemakingLevel) {
        for (String itemName : getSellableBankItems(firemakingLevel)) {
            if (countBankItem(itemName) > 1) {
                return true;
            }
        }
        return false;
    }

    public static boolean sellConfiguredBankOverflow(int firemakingLevel) {
        if (!Rs2GrandExchange.walkToGrandExchange() || !Rs2GrandExchange.openExchange()) {
            return false;
        }

        sleepUntil(Rs2GrandExchange::isOpen, 7_000);
        if (!Rs2GrandExchange.isOpen()) {
            return false;
        }

        for (String itemName : getSellableBankItems(firemakingLevel)) {
            int amountInBank = countBankItem(itemName);
            int toSell = Math.max(0, amountInBank - 1);
            if (toSell <= 0) {
                continue;
            }

            if (!ensureExchangeSlotAvailable()) {
                continue;
            }

            GrandExchangeRequest request = GrandExchangeRequest.builder()
                    .action(GrandExchangeAction.SELL)
                    .itemName(itemName)
                    .quantity(toSell)
                    .percent(-5)
                    .closeAfterCompletion(false)
                    .build();

            Rs2GrandExchange.processOffer(request);
            sleepUntil(() -> Rs2GrandExchange.hasSoldOffer() || Rs2GrandExchange.hasBoughtOffer(), SELL_WAIT_TIMEOUT_MS);
            Rs2GrandExchange.collectAllToBank();
        }

        Rs2GrandExchange.closeExchange();
        return true;
    }

    public static List<String> getSellableBankItems(int firemakingLevel) {
        List<String> sellable = new ArrayList<>(List.of(SELLABLE_BANK_ITEMS_KEEP_ONE));

        if (firemakingLevel >= LogLevels.OAK_LOGS) {
            sellable.add(NORMAL_LOGS);
        }
        if (firemakingLevel >= LogLevels.WILLOW_LOGS) {
            sellable.add(OAK_LOGS);
        }

        return sellable;
    }

    private static boolean ensureExchangeSlotAvailable() {
        if (Rs2GrandExchange.getAvailableSlotsCount() > 0) {
            return true;
        }

        Rs2GrandExchange.collectAllToBank();
        sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0, 5_000);
        return Rs2GrandExchange.getAvailableSlotsCount() > 0;
    }

    private static int countBankItem(String itemName) {
        return Rs2Bank.bankItems().stream()
                .filter(i -> i.getName() != null && i.getName().equalsIgnoreCase(itemName))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }
}
