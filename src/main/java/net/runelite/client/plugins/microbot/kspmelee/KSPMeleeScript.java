package net.runelite.client.plugins.microbot.kspmelee;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KSPMeleeScript extends Script {

    private static final String IRON_SCIMITAR = "Iron scimitar";
    private static final String BRONZE_SCIMITAR = "Bronze scimitar";
    private static final String IRON_PLATEBODY = "Iron platebody";
    private static final String BRONZE_PLATEBODY = "Bronze platebody";
    private static final String IRON_FULL_HELM = "Iron full helm";
    private static final String BRONZE_FULL_HELM = "Bronze full helm";
    private static final String IRON_PLATELEGS = "Iron platelegs";
    private static final String BRONZE_PLATELEGS = "Bronze platelegs";
    private static final String IRON_KITESHIELD = "Iron kiteshield";
    private static final String BRONZE_KITESHIELD = "Bronze kiteshield";
    private static final String AMULET_OF_POWER = "Amulet of power";
    private static final int BUY_WAIT_TIMEOUT_MS = 20000;

    private boolean setupDone;
    private Instant startedAt;
    private String status = "Idle";

    public boolean run(KSPMeleeConfig config) {
        setupDone = false;
        startedAt = Instant.now();
        status = "Starting";
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }

                if (!super.run()) {
                    return;
                }

                if (setupDone) {
                    status = "Ready";
                    return;
                }

                if (ensureStarterMeleeGear()) {
                    setupDone = true;
                    status = "Setup complete";
                    log.info("KSPMelee: Starter gear setup complete.");
                }
            } catch (Exception ex) {
                log.error("KSPMelee script error", ex);
            }
        }, 0, 1200, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean ensureStarterMeleeGear() {
        status = "Checking bank";
        if (!refreshBankItems()) {
            return false;
        }

        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        int defenceLevel = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);

        List<PurchaseRequest> purchases = new ArrayList<>();

        if (attackLevel < 5 && !hasItemAnywhere(IRON_SCIMITAR, BRONZE_SCIMITAR)) {
            purchases.add(new PurchaseRequest(IRON_SCIMITAR, BRONZE_SCIMITAR));
        }

        if (defenceLevel < 5) {
            addIfMissing(purchases, IRON_PLATEBODY, BRONZE_PLATEBODY);
            addIfMissing(purchases, IRON_FULL_HELM, BRONZE_FULL_HELM);
            addIfMissing(purchases, IRON_PLATELEGS, BRONZE_PLATELEGS);
            addIfMissing(purchases, IRON_KITESHIELD, BRONZE_KITESHIELD);
        }

        if (!hasItemAnywhere(AMULET_OF_POWER)) {
            purchases.add(new PurchaseRequest(AMULET_OF_POWER));
        }

        if (purchases.isEmpty()) {
            status = "No purchases needed";
            return true;
        }

        status = "Buying supplies";
        return buyFromGrandExchange(purchases);
    }

    private void addIfMissing(List<PurchaseRequest> purchases, String primary, String fallback) {
        if (!hasItemAnywhere(primary, fallback)) {
            purchases.add(new PurchaseRequest(primary, fallback));
        }
    }

    private boolean hasItemAnywhere(String... names) {
        for (String name : names) {
            if (Rs2Inventory.hasItem(name, true) || countBankItem(name) > 0) {
                return true;
            }
        }
        return false;
    }

    private int countBankItem(String itemName) {
        return Rs2Bank.bankItems().stream()
                .filter(item -> item.getName() != null && item.getName().equalsIgnoreCase(itemName))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private boolean refreshBankItems() {
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

    private boolean buyFromGrandExchange(List<PurchaseRequest> purchases) {
        if (!Rs2GrandExchange.walkToGrandExchange()) {
            return false;
        }

        if (!Rs2GrandExchange.openExchange()) {
            return false;
        }

        sleepUntil(Rs2GrandExchange::isOpen, 7000);
        if (!Rs2GrandExchange.isOpen()) {
            return false;
        }

        for (PurchaseRequest purchase : purchases) {
            if (alreadyOwned(purchase)) {
                continue;
            }

            if (!ensureExchangeSlotAvailable()) {
                log.warn("KSPMelee: No GE slot available for {}.", purchase.primary);
                continue;
            }

            if (!placeBuyOffer(purchase.primary)) {
                collectBoughtOffers();

                if (alreadyOwned(purchase)) {
                    continue;
                }

                if (purchase.fallback == null || !placeBuyOffer(purchase.fallback)) {
                    log.warn("KSPMelee: Failed buying {}{}.", purchase.primary,
                            purchase.fallback == null ? "" : " (fallback: " + purchase.fallback + ")");
                }
            }

            waitForPurchaseCompletion(purchase);

            collectBoughtOffers();
        }

        collectBoughtOffers();

        Rs2GrandExchange.closeExchange();
        return true;
    }

    private boolean alreadyOwned(PurchaseRequest purchase) {
        return hasItemAnywhere(purchase.primary) || (purchase.fallback != null && hasItemAnywhere(purchase.fallback));
    }

    private boolean ensureExchangeSlotAvailable() {
        if (Rs2GrandExchange.getAvailableSlotsCount() > 0) {
            return true;
        }

        collectBoughtOffers();

        sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0, 5000);
        return Rs2GrandExchange.getAvailableSlotsCount() > 0;
    }

    private void collectBoughtOffers() {
        if (!Rs2GrandExchange.hasBoughtOffer() && !Rs2GrandExchange.hasSoldOffer()) {
            return;
        }

        Rs2GrandExchange.collectAllToBank();
        sleepUntil(() -> !Rs2GrandExchange.hasBoughtOffer() && !Rs2GrandExchange.hasSoldOffer(), 5000);
    }

    private boolean placeBuyOffer(String itemName) {
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

    private void waitForPurchaseCompletion(PurchaseRequest purchase) {
        sleepUntil(() -> alreadyOwned(purchase)
                || Rs2GrandExchange.hasBoughtOffer()
                || !Rs2GrandExchange.isOpen(), BUY_WAIT_TIMEOUT_MS);
    }


    public String getStatus() {
        return status;
    }

    public String getRunningTime() {
        if (startedAt == null) {
            return "00:00:00";
        }

        Duration duration = Duration.between(startedAt, Instant.now());
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static class PurchaseRequest {
        private final String primary;
        private final String fallback;

        private PurchaseRequest(String primary) {
            this(primary, null);
        }

        private PurchaseRequest(String primary, String fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }
    }
}
