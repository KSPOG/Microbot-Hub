package net.runelite.client.plugins.microbot.sellerksp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.sellerksp.states.GeStates;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class SellerKspScript extends Script {
    private static final int SCHEDULE_INTERVAL_MS = 1000;
    private static final int COLLECT_COOLDOWN_MS = 2500;
    private static final int KEY_PRESS_DELAY_MAX = 400;
    private static final int UI_WAIT_TIMEOUT_MS = 5000;
    private static final double OFFER_REPRICE_MULTIPLIER = 0.90D;
    private static final String SELL_CONFIRM_WIDGET_TEXT = "Confirm";
    private static final String SELL_DISCOUNT_WIDGET_TEXT = "-5%";
    private static final int[] GRAND_EXCHANGE_SLOT_IDS = new int[]{30474247, 30474248, 30474249, 30474250, 30474251, 30474252, 30474253, 30474254};
    private static final Path DENYLIST_JSON_PATH = Paths.get(
            System.getProperty("user.dir"),
            "src",
            "main",
            "java",
            "net",
            "runelite",
            "client",
            "plugins",
            "microbot",
            "sellerksp",
            "json",
            "Deny.json"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GeStates state = GeStates.CHECKING;
    private boolean awaitingUiResponse = false;
    private long uiResponseWaitStart = 0L;
    private long lastCollectAt = 0L;
    private volatile boolean shuttingDown = false;
    private String statusMessage = "Idle";
    private final Set<String> runtimeDeniedItemNames = new HashSet<>();
    private final Map<GrandExchangeSlots, Long> activeSellOfferStartedAt = new EnumMap<>(GrandExchangeSlots.class);
    private final Map<GrandExchangeSlots, String> activeSellOfferFingerprints = new EnumMap<>(GrandExchangeSlots.class);
    private final Set<String> configuredDeniedItemNames = new HashSet<>();
    private String lastConfiguredBlacklistValue = null;

    @Inject
    private SellerKspConfig config;

    public GeStates getState() {
        return state;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    private void setStatusMessage(String message) {
        this.statusMessage = message;
    }

    public int getInventoryCount() {
        return Rs2Inventory.all(this::isSellableItem).size();
    }

    private boolean isAwaitingUiResponse() {
        if (!awaitingUiResponse) {
            return false;
        }
        if (System.currentTimeMillis() - uiResponseWaitStart > UI_WAIT_TIMEOUT_MS) {
            awaitingUiResponse = false;
            return false;
        }
        return true;
    }

    private void startAwaitingUiResponse() {
        awaitingUiResponse = true;
        uiResponseWaitStart = System.currentTimeMillis();
        state = GeStates.WAITING;
    }

    private void clearAwaitingUiResponse() {
        awaitingUiResponse = false;
    }

    private boolean shouldAbort() {
        return shuttingDown || Thread.currentThread().isInterrupted();
    }

    public boolean run(SellerKspPlugin plugin) {
        shutdown();
        shuttingDown = false;
        loadPersistentDeniedItems();
        Rs2AntibanSettings.naturalMouse = true;
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (shuttingDown || Thread.currentThread().isInterrupted()) {
                    return;
                }

                if (!Microbot.isLoggedIn()) {
                    return;
                }

                if (!super.run()) {
                    return;
                }

                if (isAwaitingUiResponse()) {
                    return;
                }

                if (!ensureInventoryOpen()) {
                    return;
                }

                refreshTrackedSellOffers();

                if (Rs2GrandExchange.isOpen() && Rs2GrandExchange.hasSoldOffer()) {
                    state = GeStates.SELLING;
                    if (!collectSoldOffers()) {
                        return;
                    }
                    return;
                }

                if (Rs2GrandExchange.isOpen()) {
                    state = GeStates.SELLING;
                } else if (Rs2Bank.isOpen()) {
                    state = GeStates.CHECKING;
                } else if (hasInventoryTradeableItems()) {
                    state = GeStates.SELLING;
                } else if (!hasBankTradeableItems()) {
                    finishRun(plugin);
                    return;
                } else {
                    state = GeStates.CHECKING;
                }

                switch (state) {
                    case SELLING:
                        sellInventory();
                        break;
                    case WITHDRAWING:
                    case CHECKING:
                        closeGrandExchangeIfOpen();
                        withdrawFromBank();
                        break;
                    case LOGGING_OUT:
                        logOut(plugin);
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("SellerKspScript error: " + ex.getMessage());
            }
        }, 0, SCHEDULE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean hasInventoryTradeableItems() {
        return Rs2Inventory.all(this::isSellableItem).size() > 0;
    }

    private boolean hasAnyInventoryItems() {
        return !Rs2Inventory.isEmpty();
    }

    private boolean hasBankTradeableItems() {
        boolean wasBankOpen = Rs2Bank.isOpen();
        boolean openedBank = false;
        if (!wasBankOpen) {
            if (!safeOpenBank()) {
                return false;
            }
            openedBank = true;
        }

        boolean hasItems = Rs2Bank.bankItems().stream().anyMatch(this::isSellableItem);

        if (openedBank) {
            safeCloseBank();
        }

        return hasItems;
    }

    private void withdrawFromBank() {
        if (shouldAbort() || !ensureInventoryOpen()) {
            return;
        }

        if (shouldAbort()) {
            return;
        }

        if (Rs2GrandExchange.isOpen() && Rs2GrandExchange.hasSoldOffer()) {
            collectSoldOffers();
            return;
        }

        state = GeStates.CHECKING;
        if (shouldAbort() || !safeOpenBank()) {
            return;
        }

        if (!hasBankTradeableItems()) {
            safeCloseBank();
            state = GeStates.LOGGING_OUT;
            return;
        }

        if (shouldAbort()) {
            return;
        }

        if (hasAnyInventoryItems()) {
            setStatusMessage("Depositing Unsellable Items");
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, UI_WAIT_TIMEOUT_MS);
        }

        if (shouldAbort()) {
            return;
        }

        if (!Rs2Bank.hasWithdrawAsNote()) {
            Rs2Bank.setWithdrawAsNote();
            sleepUntil(Rs2Bank::hasWithdrawAsNote, 2000);
        }

        setStatusMessage("Withdrawing items");
        for (Rs2ItemModel item : Rs2Bank.bankItems()) {
            if (shouldAbort()) {
                return;
            }
            if (Rs2Inventory.isFull()) {
                break;
            }
            if (!isSellableItem(item)) {
                continue;
            }
            Rs2Bank.withdrawAll(item.getId());
            sleepUntil(() -> Rs2Inventory.hasItem(item.getName(), true), UI_WAIT_TIMEOUT_MS);
        }

        safeCloseBank();
    }

    private boolean ensureInventoryOpen() {
        if (shuttingDown) {
            return false;
        }

        if (Rs2Inventory.isOpen()) {
            clearAwaitingUiResponse();
            return true;
        }

        setStatusMessage("Opening Inventory");
        if (!Rs2Inventory.open()) {
            startAwaitingUiResponse();
            return false;
        }

        boolean opened = sleepUntil(Rs2Inventory::isOpen, UI_WAIT_TIMEOUT_MS);
        if (!opened) {
            startAwaitingUiResponse();
            return false;
        }

        clearAwaitingUiResponse();
        return true;
    }

    private boolean safeOpenBank() {
        if (shuttingDown) {
            return false;
        }
        if (Rs2Bank.isOpen()) {
            clearAwaitingUiResponse();
            return true;
        }
        state = GeStates.CHECKING;
        setStatusMessage("Banking");
        if (!Rs2Bank.openBank()) {
            startAwaitingUiResponse();
            return false;
        }
        boolean opened = sleepUntil(Rs2Bank::isOpen, UI_WAIT_TIMEOUT_MS);
        if (!opened) {
            startAwaitingUiResponse();
            return false;
        }
        clearAwaitingUiResponse();
        return true;
    }

    private boolean safeCloseBank() {
        if (shuttingDown) {
            return false;
        }
        if (!Rs2Bank.isOpen()) {
            clearAwaitingUiResponse();
            return true;
        }
        Rs2Bank.closeBank();
        boolean closed = sleepUntil(() -> !Rs2Bank.isOpen(), UI_WAIT_TIMEOUT_MS);
        if (!closed) {
            startAwaitingUiResponse();
            return false;
        }
        clearAwaitingUiResponse();
        return true;
    }

    private boolean safeOpenGrandExchange() {
        if (shuttingDown) {
            return false;
        }
        if (Rs2GrandExchange.isOpen()) {
            clearAwaitingUiResponse();
            return true;
        }
        state = GeStates.SELLING;
        setStatusMessage("Opening GE");
        if (!Rs2GrandExchange.openExchange()) {
            startAwaitingUiResponse();
            return false;
        }
        boolean opened = sleepUntil(Rs2GrandExchange::isOpen, UI_WAIT_TIMEOUT_MS);
        if (!opened) {
            startAwaitingUiResponse();
            return false;
        }
        clearAwaitingUiResponse();
        return true;
    }

    private boolean safeCloseGrandExchange() {
        if (shuttingDown) {
            return false;
        }
        if (!Rs2GrandExchange.isOpen()) {
            clearAwaitingUiResponse();
            return true;
        }
        Rs2GrandExchange.closeExchange();
        boolean closed = sleepUntil(() -> !Rs2GrandExchange.isOpen(), UI_WAIT_TIMEOUT_MS);
        if (!closed) {
            startAwaitingUiResponse();
            return false;
        }
        clearAwaitingUiResponse();
        return true;
    }

    private void sellInventory() {
        if (shouldAbort()) {
            return;
        }

        state = GeStates.SELLING;
        List<Rs2ItemModel> items = Rs2Inventory.all(this::isSellableItem);
        if (items.isEmpty()) {
            return;
        }

        setStatusMessage("Setting Sale offers");

        if (shouldAbort() || !safeOpenGrandExchange()) {
            return;
        }

        if (repriceStaleSellOffers()) {
            return;
        }

        int availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
        if (availableSlots == 0) {
            while (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                if (shouldAbort()) {
                    return;
                }
                if (!Rs2GrandExchange.isOpen()) {
                    if (!safeOpenGrandExchange()) {
                        return;
                    }
                }

                if (Rs2GrandExchange.hasSoldOffer() && !collectSoldOffers()) {
                    return;
                }

                if (repriceStaleSellOffers()) {
                    return;
                }

                sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0
                        || Rs2GrandExchange.hasSoldOffer()
                        || !Rs2GrandExchange.isOpen(), UI_WAIT_TIMEOUT_MS);
            }
            availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
        }

        int itemsToSell = Math.min(items.size(), availableSlots);
        for (int i = 0; i < itemsToSell; i++) {
            if (shouldAbort()) {
                return;
            }
            Rs2ItemModel item = items.get(i);
            if (!createSellOfferUsingQuickPrice(item)) {
                break;
            }
        }
    }

    private void refreshTrackedSellOffers() {
        long now = System.currentTimeMillis();
        Set<GrandExchangeSlots> activeSlots = new HashSet<>();
        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
            if (details == null || !details.isSelling() || details.isCompleted()) {
                continue;
            }

            activeSlots.add(slot);
            String fingerprint = buildOfferFingerprint(details);
            String previousFingerprint = activeSellOfferFingerprints.get(slot);
            if (!fingerprint.equals(previousFingerprint)) {
                activeSellOfferStartedAt.put(slot, now);
                activeSellOfferFingerprints.put(slot, fingerprint);
            } else {
                activeSellOfferStartedAt.putIfAbsent(slot, now);
            }
        }

        activeSellOfferStartedAt.keySet().removeIf(slot -> !activeSlots.contains(slot));
        activeSellOfferFingerprints.keySet().removeIf(slot -> !activeSlots.contains(slot));
    }

    private String buildOfferFingerprint(GrandExchangeOfferDetails details) {
        if (details == null) {
            return "";
        }

        return (details.getItemName() == null ? "" : details.getItemName().toLowerCase(Locale.ENGLISH))
                + "|" + details.getPrice()
                + "|" + details.getTotalQuantity()
                + "|" + details.getQuantitySold();
    }

    private boolean repriceStaleSellOffers() {
        long now = System.currentTimeMillis();
        long repriceTimeoutMs = TimeUnit.MINUTES.toMillis(Math.max(1, config != null ? config.changePriceTimeMinutes() : 2));
        for (Map.Entry<GrandExchangeSlots, Long> entry : activeSellOfferStartedAt.entrySet()) {
            if ((now - entry.getValue()) < repriceTimeoutMs) {
                continue;
            }

            if (repriceSellOffer(entry.getKey())) {
                activeSellOfferStartedAt.put(entry.getKey(), now);
                return true;
            }
        }

        return false;
    }

    private boolean repriceSellOffer(GrandExchangeSlots slot) {
        if (shouldAbort()) {
            return false;
        }

        GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
        if (details == null || !details.isSelling() || details.isCompleted()) {
            activeSellOfferStartedAt.remove(slot);
            activeSellOfferFingerprints.remove(slot);
            return false;
        }

        int currentPrice = details.getPrice();
        int loweredPrice = Math.max(1, (int) Math.floor(currentPrice * OFFER_REPRICE_MULTIPLIER));
        if (loweredPrice >= currentPrice) {
            loweredPrice = Math.max(1, currentPrice - 1);
        }
        final int targetPrice = loweredPrice;

        if (!openModifyOffer(slot)) {
            return false;
        }

        boolean modified = applyQuickSellDiscountAndConfirm();
        if (modified) {
            setStatusMessage("Lowering " + details.getItemName() + " to " + targetPrice);
        }
        return modified;
    }

    private boolean openModifyOffer(GrandExchangeSlots slot) {
        if (shouldAbort()) {
            return false;
        }

        int slotIndex = slot.ordinal();
        if (slotIndex < 0 || slotIndex >= GRAND_EXCHANGE_SLOT_IDS.length) {
            return false;
        }

        Widget slotWidget = Rs2Widget.getWidget(GRAND_EXCHANGE_SLOT_IDS[slotIndex]);
        if (slotWidget == null || !Rs2Widget.isWidgetVisible(slotWidget.getId())) {
            return false;
        }

        Rectangle bounds = slotWidget.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(slotWidget.getBounds())
                ? slotWidget.getBounds()
                : Rs2UiHelper.getDefaultRectangle();
        if (Rs2AntibanSettings.naturalMouse && bounds != null) {
            Microbot.getMouse().move(bounds);
            sleep(120, 220);
        }
        NewMenuEntry menuEntry = new NewMenuEntry("Modify offer", "", 3, MenuAction.CC_OP, 2, slotWidget.getId(), false);
        Microbot.doInvoke(menuEntry, bounds);
        return sleepUntil(this::isSellOfferSetupVisible, UI_WAIT_TIMEOUT_MS);
    }

    private boolean collectSoldOffers() {
        if (shouldAbort()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if ((now - lastCollectAt) < COLLECT_COOLDOWN_MS) {
            return false;
        }

        setStatusMessage("Collecting Sold Offers");
        lastCollectAt = now;
        Rs2GrandExchange.collectAllToBank();
        sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), UI_WAIT_TIMEOUT_MS);
        refreshTrackedSellOffers();
        return true;
    }

    private boolean createSellOfferUsingQuickPrice(Rs2ItemModel item) {
        if (shouldAbort() || item == null || item.getName() == null) {
            return false;
        }

        setStatusMessage("Selling " + item.getName());

        if (!Rs2Inventory.interact(item.getName(), "Offer")) {
            denyItemForSession(item.getName(), "Unable to create GE offer interaction");
            startAwaitingUiResponse();
            return false;
        }

        if (shouldAbort()) {
            return false;
        }

        boolean offerScreenOpened = sleepUntil(this::isSellOfferSetupVisible, UI_WAIT_TIMEOUT_MS);
        if (!offerScreenOpened) {
            denyItemForSession(item.getName(), "GE offer setup did not open");
            startAwaitingUiResponse();
            return false;
        }

        if (shouldAbort()) {
            return false;
        }

        if (!applyQuickSellDiscountAndConfirm()) {
            denyItemForSession(item.getName(), "GE quick-price flow failed");
            if (Rs2GrandExchange.isOfferScreenOpen()) {
                Rs2GrandExchange.backToOverview();
                sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 2000);
            }
            startAwaitingUiResponse();
            return false;
        }

        boolean offered = sleepUntil(() -> !Rs2Inventory.hasItem(item.getName(), true), UI_WAIT_TIMEOUT_MS);
        if (!offered) {
            denyItemForSession(item.getName(), "GE offer was not placed successfully");
            startAwaitingUiResponse();
            return false;
        }

        refreshTrackedSellOffers();
        clearAwaitingUiResponse();
        return true;
    }

    private boolean isSellableItem(Rs2ItemModel item) {
        if (item == null) {
            return false;
        }

        if (isDeniedByName(item)) {
            return false;
        }

        if (item.isTradeable()) {
            return true;
        }

        ItemComposition composition = item.getItemComposition();
        return composition != null && composition.isMembers();
    }

    private boolean isDeniedByName(Rs2ItemModel item) {
        if (item == null || item.getName() == null) {
            return false;
        }

        String normalizedName = item.getName().toLowerCase(Locale.ENGLISH);
        refreshConfiguredDeniedItems();
        return runtimeDeniedItemNames.contains(normalizedName)
                || configuredDeniedItemNames.contains(normalizedName);
    }

    private void refreshConfiguredDeniedItems() {
        String rawBlacklist = config != null ? config.itemBlacklist() : "";
        if (rawBlacklist == null) {
            rawBlacklist = "";
        }

        if (rawBlacklist.equals(lastConfiguredBlacklistValue)) {
            return;
        }

        configuredDeniedItemNames.clear();
        lastConfiguredBlacklistValue = rawBlacklist;

        String[] entries = rawBlacklist.split("[,;\\r\\n]+");
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }

            String normalizedName = entry.trim().toLowerCase(Locale.ENGLISH);
            if (!normalizedName.isEmpty()) {
                configuredDeniedItemNames.add(normalizedName);
            }
        }
    }

    private void denyItemForSession(String itemName, String reason) {
        if (itemName == null || itemName.isEmpty()) {
            return;
        }

        String normalizedName = itemName.toLowerCase(Locale.ENGLISH);
        if (runtimeDeniedItemNames.add(normalizedName)) {
            Microbot.log("SellerKspScript: denying item for this session: " + itemName + " (" + reason + ")");
            savePersistentDeniedItems();
        }
    }

    private void loadPersistentDeniedItems() {
        runtimeDeniedItemNames.clear();
        if (!Files.exists(DENYLIST_JSON_PATH)) {
            return;
        }

        try {
            String rawValue = Files.readString(DENYLIST_JSON_PATH, StandardCharsets.UTF_8);
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return;
            }

            DenyListData denyListData = GSON.fromJson(rawValue, DenyListData.class);
            if (denyListData == null || denyListData.deniedItems == null) {
                return;
            }

            for (String entry : denyListData.deniedItems) {
                if (entry == null) {
                    continue;
                }

                String normalizedName = entry.trim().toLowerCase(Locale.ENGLISH);
                if (!normalizedName.isEmpty()) {
                    runtimeDeniedItemNames.add(normalizedName);
                }
            }
        } catch (IOException ex) {
            Microbot.log("SellerKspScript: failed to read denylist json: " + ex.getMessage());
        }
    }

    private void savePersistentDeniedItems() {
        DenyListData denyListData = new DenyListData();
        denyListData.deniedItems.addAll(new TreeSet<>(runtimeDeniedItemNames));

        try {
            Files.createDirectories(DENYLIST_JSON_PATH.getParent());
            Files.writeString(
                    DENYLIST_JSON_PATH,
                    GSON.toJson(denyListData),
                    StandardCharsets.UTF_8
            );
        } catch (IOException ex) {
            Microbot.log("SellerKspScript: failed to write denylist json: " + ex.getMessage());
        }
    }

    public void clearPersistentDeniedItems() {
        runtimeDeniedItemNames.clear();
        savePersistentDeniedItems();
        Microbot.log("SellerKspScript: cleared persistent denylist");
    }

    private boolean applyQuickSellDiscountAndConfirm() {
        if (shouldAbort()) {
            return false;
        }

        if (!sleepUntil(this::isSellOfferSetupVisible, UI_WAIT_TIMEOUT_MS)) {
            return false;
        }

        if (shouldAbort()) {
            return false;
        }

        if (!clickWidgetByText(SELL_DISCOUNT_WIDGET_TEXT)) {
            return false;
        }

        if (shouldAbort()) {
            return false;
        }

        if (!sleepUntil(this::isSellOfferSetupVisible, UI_WAIT_TIMEOUT_MS)) {
            return false;
        }

        if (shouldAbort()) {
            return false;
        }

        if (!clickWidgetByText(SELL_DISCOUNT_WIDGET_TEXT)) {
            return false;
        }

        if (shouldAbort()) {
            return false;
        }

        boolean confirmReady = sleepUntil(this::isSellConfirmVisible, UI_WAIT_TIMEOUT_MS);
        if (!confirmReady) {
            return false;
        }

        if (shouldAbort()) {
            return false;
        }

        if (!clickWidgetByText(SELL_CONFIRM_WIDGET_TEXT)) {
            return false;
        }

        return sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), UI_WAIT_TIMEOUT_MS);
    }

    private boolean isSellOfferSetupVisible() {
        return Rs2GrandExchange.isOfferScreenOpen()
                || isWidgetVisibleByText(SELL_DISCOUNT_WIDGET_TEXT)
                || isSellConfirmVisible();
    }

    private boolean isSellConfirmVisible() {
        return isWidgetVisibleByText(SELL_CONFIRM_WIDGET_TEXT);
    }

    private boolean isWidgetVisibleByText(String text) {
        Widget widget = Rs2Widget.findWidget(text, null, false);
        if (widget == null || !Rs2Widget.isWidgetVisible(widget.getId())) {
            widget = Rs2Widget.findWidget(text, null, true);
        }

        return widget != null && Rs2Widget.isWidgetVisible(widget.getId());
    }

    private boolean clickWidgetByText(String text) {
        Widget widget = Rs2Widget.findWidget(text, null, false);
        if (widget == null || !Rs2Widget.isWidgetVisible(widget.getId())) {
            widget = Rs2Widget.findWidget(text, null, true);
        }

        if (widget == null || !Rs2Widget.isWidgetVisible(widget.getId())) {
            return false;
        }

        Rs2Widget.clickWidget(widget);
        sleepUntil(() -> {
            Widget refreshedWidget = Rs2Widget.findWidget(text, null, false);
            if (refreshedWidget == null) {
                refreshedWidget = Rs2Widget.findWidget(text, null, true);
            }
            return refreshedWidget == null
                    || !Rs2Widget.isWidgetVisible(refreshedWidget.getId())
                    || text.toLowerCase(Locale.ENGLISH).contains("confirm");
        }, KEY_PRESS_DELAY_MAX);
        return true;
    }

    private void closeGrandExchangeIfOpen() {
        safeCloseGrandExchange();
    }

    private void logOut(SellerKspPlugin plugin) {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;
        closeGrandExchangeIfOpen();
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
        }
        Rs2Player.logout();
        shutdown();
        Microbot.stopPlugin(plugin);
    }

    private void finishRun(SellerKspPlugin plugin) {
        if (config != null && config.logoutWhenOutOfItems()) {
            logOut(plugin);
            return;
        }

        shuttingDown = true;
        closeGrandExchangeIfOpen();
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
        }
        shutdown();
        Microbot.stopPlugin(plugin);
    }

    @Override
    public void shutdown() {
        shuttingDown = true;
        awaitingUiResponse = false;
        activeSellOfferStartedAt.clear();
        activeSellOfferFingerprints.clear();
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        state = GeStates.CHECKING;
        lastCollectAt = 0L;
        statusMessage = "Stopped";
        super.shutdown();
    }

    private static final class DenyListData {
        private final Set<String> deniedItems = new TreeSet<>();
    }
}
