package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.settings.Rs2Settings;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;

@Singleton
public class KspF2PHighAlchTraderScript extends Script {
    public enum State {
        STARTING,
        SCANNING_MARKET,
        PREPARING_SUPPLIES,
        PREPARING_STOCK,
        PLACING_BUY,
        WAITING_FOR_BUY,
        PLACING_BASKET,
        WAITING_FOR_BASKET,
        PREPARING_ALCH,
        ALCHING,
        NO_PROFITABLE_ITEM,
        ERROR
    }

    private enum PurchaseType {
        NATURE_RUNES,
        FIRE_RUNES,
        ALCHABLE
    }

    private static final class BasketOffer {
        private final AlchOpportunity opportunity;
        private final int quantity;
        private final int price;
        private GrandExchangeSlots slot;
        private long startedAt;
        private long collectReadyAt;
        private long abortCollectReadyAt;
        private int boughtAtAbort;
        private boolean aborted;
        private boolean resolved;

        private BasketOffer(AlchOpportunity opportunity, int quantity, int price) {
            this.opportunity = opportunity;
            this.quantity = quantity;
            this.price = price;
        }
    }

    private static final int NATURE_RUNE_ID = 561;
    private static final int FIRE_RUNE_ID = 554;
    private static final int COINS_ID = 995;
    private static final int STAFF_OF_FIRE_ID = 1387;

    private static final int HIGH_ALCH_CASTS_PER_HOUR = 1200;
    private static final long HIGH_ALCH_COOLDOWN_MS = 3000L;
    private static final double RUNE_BUY_MULTIPLIER = 1.03;
    private static final int TICK_INTERVAL_MS = 600;
    private static final long FOUR_HOURS_MS = TimeUnit.HOURS.toMillis(4);
    private static final long ZERO_FILL_BLOCK_MS = TimeUnit.MINUTES.toMillis(2);
    private static final long GE_TRAVEL_RETRY_MS = 4000L;

    private KspF2PHighAlchTraderConfig config;
    private volatile State state = State.STARTING;
    private volatile String status = "Starting";
    private volatile AlchOpportunity activeOpportunity;

    private final List<AlchOpportunity> rankedOpportunities = new CopyOnWriteArrayList<>();
    private final Map<Integer, Integer> purchasedInWindow = new HashMap<>();
    private final Map<Integer, Long> purchaseWindowStartedAtByItem = new HashMap<>();
    private final Map<Integer, Long> blockedUntil = new HashMap<>();
    // Items whose alchable GE offers reached the configured timeout are temporarily
    // skipped so an illiquid item cannot consume a GE slot every basket cycle.
    private final Map<Integer, Long> slowBuyCooldownUntil = new ConcurrentHashMap<>();
    // When optional bank-stock mode checks an item and finds none, remember that result
    // for the session instead of reopening the bank on every PREPARING_STOCK pass.
    private final Set<Integer> bankStockExhaustedItemIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> bankSupplyExhaustedItemIds = ConcurrentHashMap.newKeySet();
    // These two maps are read by the RuneLite overlay thread while the script worker
    // updates them. ConcurrentHashMap prevents transient render failures while offers
    // are collected/queued.
    private final Map<Integer, Integer> queuedStockByItem = new ConcurrentHashMap<>();
    private final Map<Integer, AlchOpportunity> queuedOpportunityByItem = new ConcurrentHashMap<>();
    private final Map<Integer, Long> inventoryCollectedAtByItem = new HashMap<>();
    // Basket size is tiny (3 slots on F2P), so CopyOnWriteArrayList is a good fit and
    // gives the overlay a stable snapshot while the worker resolves/clears offers.
    private final List<BasketOffer> pendingBasket = new CopyOnWriteArrayList<>();
    private final KspHighAlchAntiban customAntiban = new KspHighAlchAntiban();
    private final KspGeInteraction geInteraction = new KspGeInteraction();

    private long lastMarketScanAt = 0L;
    private long startedAt = 0L;
    private long projectedProfit = 0L;
    private long castsCompleted = 0L;
    private long nextCastAllowedAt = 0L;
    private int committedItemId = 0;
    private int committedStockRemaining = 0;
    private int failedCastAttempts = 0;
    private int basketPlacementIndex = 0;
    private int cleanupItemId = 0;

    private boolean usingFireStaff = true;
    private boolean fireStaffUnavailable = false;
    private boolean bankCoinsExhausted = false;
    private volatile boolean accountMembershipActive = false;
    private volatile boolean currentWorldMembers = false;
    private volatile boolean membersContentEnabled = false;

    private PurchaseType pendingPurchaseType;
    private int pendingItemId;
    private String pendingItemName;
    private int pendingQuantity;
    private int pendingPrice;
    private long pendingOfferStartedAt;
    private long pendingCollectReadyAt;
    private long pendingAbortCollectReadyAt;
    private int pendingBoughtAtAbort;
    private boolean pendingAborted;
    private GrandExchangeSlots pendingSlot;

    private volatile boolean shuttingDown = false;
    private long nextGeTravelAttemptAt = 0L;

    public synchronized boolean run(KspF2PHighAlchTraderConfig config) {
        // Plugin/hot-reload start events can occasionally call run() more than once on the
        // same singleton script. Never overwrite mainScheduledFuture while an older loop is
        // still alive; doing so leaves an orphan worker that continues after shutdown.
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone() && !mainScheduledFuture.isCancelled()) {
            this.config = config;
            status = "Already running";
            return true;
        }
        this.shuttingDown = false;
        this.config = config;
        this.startedAt = System.currentTimeMillis();
        this.lastMarketScanAt = 0L;
        this.projectedProfit = 0L;
        this.castsCompleted = 0L;
        this.nextCastAllowedAt = 0L;
        this.committedItemId = 0;
        this.committedStockRemaining = 0;
        this.failedCastAttempts = 0;
        this.usingFireStaff = config.useFireStaff();
        this.fireStaffUnavailable = false;
        this.bankCoinsExhausted = false;
        this.activeOpportunity = null;
        this.rankedOpportunities.clear();
        this.purchasedInWindow.clear();
        this.purchaseWindowStartedAtByItem.clear();
        this.blockedUntil.clear();
        this.slowBuyCooldownUntil.clear();
        this.bankStockExhaustedItemIds.clear();
        this.bankSupplyExhaustedItemIds.clear();
        this.queuedStockByItem.clear();
        this.queuedOpportunityByItem.clear();
        this.inventoryCollectedAtByItem.clear();
        this.pendingBasket.clear();
        this.basketPlacementIndex = 0;
        this.cleanupItemId = 0;
        if (config.customAntiban()) {
            this.customAntiban.reset(config.antibanProfile());
        } else {
            this.customAntiban.disabled();
        }
        clearPendingPurchase();
        this.state = State.STARTING;
        this.status = "Starting";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (shuttingDown) {
                    return;
                }
                if (!Microbot.isLoggedIn() || !super.run()) {
                    return;
                }
                tick();
            } catch (Exception ex) {
                // ClientThread.invoke throws RuntimeException(cause=InterruptedException)
                // when a script is cancelled while waiting on the client thread. That is a
                // normal shutdown path, not a trader failure, so do not convert it to ERROR.
                if (isExpectedInterruption(ex)) {
                    return;
                }
                state = State.ERROR;
                status = "Error: " + ex.getClass().getSimpleName();
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void tick() {
        if (shuttingDown) {
            return;
        }
        resetExpiredPurchaseWindows();

        if (getMagicLevel() < 55) {
            state = State.ERROR;
            status = "55 Magic required";
            return;
        }

        if (activeOpportunity != null && committedStockRemaining <= 0 && queuedStockByItem.isEmpty() && isMarketRefreshDue() &&
                state != State.WAITING_FOR_BUY && state != State.PLACING_BUY &&
                state != State.PLACING_BASKET && state != State.WAITING_FOR_BASKET && state != State.ALCHING) {
            state = State.SCANNING_MARKET;
        }

        if (config.customAntiban() && state == State.ALCHING && customAntiban.isPaused()) {
            status = "Anti-ban: " + customAntiban.getActivity();
            Microbot.status = "KSP High Alch Trader: " + status;
            return;
        }

        switch (state) {
            case STARTING:
                status = "Initial market scan";
                state = State.SCANNING_MARKET;
                break;
            case SCANNING_MARKET:
                scanMarket();
                break;
            case PREPARING_SUPPLIES:
                prepareSupplies();
                break;
            case PREPARING_STOCK:
                prepareStock();
                break;
            case PLACING_BUY:
                placePendingBuy();
                break;
            case WAITING_FOR_BUY:
                waitForPendingBuy();
                break;
            case PLACING_BASKET:
                placeBasketOffers();
                break;
            case WAITING_FOR_BASKET:
                waitForBasketOffers();
                break;
            case PREPARING_ALCH:
                prepareAlchInventory();
                break;
            case ALCHING:
                alchNextItem();
                break;
            case NO_PROFITABLE_ITEM:
                status = "No profitable item - waiting for refresh";
                if (isMarketRefreshDue()) {
                    state = State.SCANNING_MARKET;
                }
                break;
            case ERROR:
                // Remain stopped until the user fixes the stated condition or restarts the plugin.
                break;
            default:
                state = State.SCANNING_MARKET;
                break;
        }

        Microbot.status = "KSP High Alch Trader: " + status;
    }

    /**
     * Re-evaluate access to members-only items. If membership/world mode changes while
     * the plugin is running, force a fresh market scan before any additional GE offers
     * are planned so stale members-only opportunities are never submitted in F2P mode.
     */
    private boolean refreshMembersContentMode() {
        accountMembershipActive = Rs2Player.isMember();
        currentWorldMembers = Rs2Player.isInMemberWorld();
        boolean current = accountMembershipActive && currentWorldMembers;
        boolean changed = current != membersContentEnabled;
        membersContentEnabled = current;
        return changed;
    }

    private void scanMarket() {
        state = State.SCANNING_MARKET;
        refreshMembersContentMode();
        status = membersContentEnabled
                ? "Scanning F2P + members alch margins"
                : "Scanning F2P alch margins";
        rankedOpportunities.clear();

        WikiPrice naturePriceData = Rs2GrandExchange.getRealTimePrices(NATURE_RUNE_ID);
        if (naturePriceData == null || naturePriceData.buyPrice <= 0) {
            status = "Nature rune price unavailable";
            lastMarketScanAt = System.currentTimeMillis();
            state = State.NO_PROFITABLE_ITEM;
            return;
        }

        int natureRunePrice = conservativeRuneBuyPrice(naturePriceData.buyPrice);
        int fireRunePrice = 0;
        if (!usingFireStaff) {
            WikiPrice firePriceData = Rs2GrandExchange.getRealTimePrices(FIRE_RUNE_ID);
            if (firePriceData == null || firePriceData.buyPrice <= 0) {
                status = "Fire rune price unavailable";
                lastMarketScanAt = System.currentTimeMillis();
                state = State.NO_PROFITABLE_ITEM;
                return;
            }
            fireRunePrice = conservativeRuneBuyPrice(firePriceData.buyPrice);
        }

        Set<Integer> candidates = F2PAlchCatalog.buildCandidateSet(config, membersContentEnabled);
        long now = System.currentTimeMillis();

        for (int itemId : candidates) {
            if (blockedUntil.getOrDefault(itemId, 0L) > now) {
                continue;
            }

            ItemComposition composition = getItemComposition(itemId);
            if (composition == null
                    || (composition.isMembers() && !membersContentEnabled)
                    || !composition.isGeTradeable()
                    || composition.getHaPrice() <= 0) {
                continue;
            }

            WikiPrice price = Rs2GrandExchange.getRealTimePrices(itemId);
            if (price == null || price.buyPrice <= 0) {
                continue;
            }
            if (price.volume > 0 && price.volume < config.minimumVolume()) {
                continue;
            }

            int fireCost = usingFireStaff ? 0 : fireRunePrice * 5;
            int profit = composition.getHaPrice() - price.buyPrice - natureRunePrice - fireCost;
            long expectedGpPerHour = (long) profit * HIGH_ALCH_CASTS_PER_HOUR;

            if (profit < config.minimumProfitPerCast() || expectedGpPerHour < config.minimumExpectedGpPerHour()) {
                continue;
            }

            rankedOpportunities.add(new AlchOpportunity(
                    itemId,
                    composition.getMembersName(),
                    price.buyPrice,
                    composition.getHaPrice(),
                    natureRunePrice,
                    fireCost,
                    profit,
                    expectedGpPerHour,
                    price.volume,
                    -1,
                    now
            ));
        }

        rankedOpportunities.sort(Comparator
                .comparingLong(AlchOpportunity::getExpectedProfitPerHour)
                .thenComparingInt(AlchOpportunity::getProfitPerCast)
                .reversed());

        lastMarketScanAt = now;

        if (rankedOpportunities.isEmpty()) {
            activeOpportunity = null;
            state = State.NO_PROFITABLE_ITEM;
            status = "No candidate meets profit filters";
            return;
        }

        AlchOpportunity best = withTradeLimit(rankedOpportunities.get(0));
        if (best == null || getRemainingTradeLimit(best) <= 0) {
            long blockUntil = best != null && best.getTradeLimitPer4Hours() > 0
                    ? getPurchaseWindowEnd(best.getItemId(), now)
                    : now + ZERO_FILL_BLOCK_MS;
            blockItemUntil(rankedOpportunities.get(0).getItemId(), blockUntil);
            state = State.SCANNING_MARKET;
            return;
        }

        activeOpportunity = best;
        status = "Selected " + best.getItemName() + " (" + best.getProfitPerCast() + " gp/alch)";
        state = State.PREPARING_SUPPLIES;
    }

    private AlchOpportunity withTradeLimit(AlchOpportunity base) {
        // Only the selected candidate gets a mapping lookup. Besides the GE limit, this gives
        // us a second authoritative sanity check for membership and High Alch metadata.
        ItemMappingData mapping = Rs2GrandExchange.getItemMappingData(base.getItemId());
        if (mapping == null) {
            // Built-in candidates are curated. Extra user-supplied IDs require mapping validation
            // before the plugin is allowed to buy them.
            return F2PAlchCatalog.isBuiltIn(base.getItemId()) ? base : null;
        }
        if ((mapping.members && !membersContentEnabled) || mapping.highAlch <= 0) {
            return null;
        }

        int highAlchValue = mapping.highAlch;
        int profitPerCast = highAlchValue
                - base.getInstantBuyPrice()
                - base.getNatureRunePrice()
                - base.getFireRuneCost();
        long expectedPerHour = (long) profitPerCast * HIGH_ALCH_CASTS_PER_HOUR;

        if (profitPerCast < config.minimumProfitPerCast() || expectedPerHour < config.minimumExpectedGpPerHour()) {
            return null;
        }

        return new AlchOpportunity(
                base.getItemId(),
                mapping.name == null || mapping.name.isBlank() ? base.getItemName() : mapping.name,
                base.getInstantBuyPrice(),
                highAlchValue,
                base.getNatureRunePrice(),
                base.getFireRuneCost(),
                profitPerCast,
                expectedPerHour,
                base.getVolume(),
                config.respectGeLimits() ? mapping.tradeLimitPer4Hours : -1,
                base.getTimestamp()
        );
    }

    private void prepareSupplies() {
        if (activeOpportunity == null) {
            state = State.SCANNING_MARKET;
            return;
        }

        if (config.useFireStaff() && !isUsingFireStaff()) {
            if (tryEquipFireStaff()) {
                if (!usingFireStaff) {
                    usingFireStaff = true;
                    state = State.SCANNING_MARKET;
                    status = "Fire staff equipped - rescanning margins";
                    return;
                }
                usingFireStaff = true;
            } else if (config.fallbackToFireRunes()) {
                if (usingFireStaff) {
                    usingFireStaff = false;
                    state = State.SCANNING_MARKET;
                    status = "No fire staff - pricing Fire runes";
                    return;
                }
            } else {
                state = State.ERROR;
                status = "Missing Staff of fire";
                return;
            }
        }

        if (Rs2Inventory.itemQuantity(NATURE_RUNE_ID) < config.minimumNatureRunes()) {
            if (withdrawExistingRunes(NATURE_RUNE_ID)) {
                return;
            }
            int needed = Math.max(1, config.natureRuneTarget() - Rs2Inventory.itemQuantity(NATURE_RUNE_ID));
            prepareRunePurchase(PurchaseType.NATURE_RUNES, NATURE_RUNE_ID, "Nature rune", needed);
            return;
        }

        if (!usingFireStaff) {
            int minimumFire = Math.max(25, getAlchBatchSize() * 5);
            if (Rs2Inventory.itemQuantity(FIRE_RUNE_ID) < minimumFire) {
                if (withdrawExistingRunes(FIRE_RUNE_ID)) {
                    return;
                }
                int needed = Math.max(5, config.fireRuneTarget() - Rs2Inventory.itemQuantity(FIRE_RUNE_ID));
                prepareRunePurchase(PurchaseType.FIRE_RUNES, FIRE_RUNE_ID, "Fire rune", needed);
                return;
            }
        }

        state = State.PREPARING_STOCK;
    }

    private boolean withdrawExistingRunes(int runeId) {
        if (bankSupplyExhaustedItemIds.contains(runeId)) {
            return false;
        }
        if (!openBankSafely()) {
            status = "Opening bank for runes";
            return true;
        }
        if (Rs2Bank.hasItem(runeId)) {
            Rs2Bank.withdrawAll(runeId);
            sleepUntil(() -> Rs2Inventory.itemQuantity(runeId) > 0, 3000);
            Rs2Bank.closeBank();
            status = "Withdrew runes";
            return true;
        }
        bankSupplyExhaustedItemIds.add(runeId);
        Rs2Bank.closeBank();
        return false;
    }

    private void prepareRunePurchase(PurchaseType type, int itemId, String itemName, int quantity) {
        WikiPrice price = Rs2GrandExchange.getRealTimePrices(itemId);
        if (price == null || price.buyPrice <= 0) {
            state = State.ERROR;
            status = itemName + " price unavailable";
            return;
        }

        int buyPrice = conservativeRuneBuyPrice(price.buyPrice);
        preparePendingPurchase(type, itemId, itemName, quantity, buyPrice);
    }

    private void prepareStock() {
        if (refreshMembersContentMode()) {
            state = State.SCANNING_MARKET;
            status = membersContentEnabled
                    ? "Members mode detected - refreshing market"
                    : "F2P mode detected - refreshing market";
            return;
        }
        // Inventory always has priority over creating new GE offers. This includes
        // profitable supported items that were already present in inventory and are
        // not represented by the internal GE queue. Alch them first, then return to
        // stock planning only after the inventory contains no supported profitable stock.
        if (activateProfitableInventoryStock()) {
            state = State.PREPARING_ALCH;
            status = "Inventory-first: " + activeOpportunity.getItemName();
            return;
        }

        // A Withdraw-All batch can include pre-existing stock. Once the plugin-owned
        // quantity reaches zero, bank any remainder before selecting the next item.
        // The inventory-first check above intentionally runs before cleanup so a
        // supported profitable remainder is alched instead of needlessly banked.
        if (cleanupItemId > 0) {
            cleanupFinishedStock();
            if (cleanupItemId > 0) {
                return;
            }
        }

        // Continue already purchased basket stock before creating new GE offers.
        if (activateNextQueuedStock()) {
            state = State.PREPARING_ALCH;
            status = "Queued stock: " + activeOpportunity.getItemName();
            return;
        }

        if (activeOpportunity == null || isMarketRefreshDue()) {
            state = State.SCANNING_MARKET;
            status = "Refreshing market before basket";
            return;
        }

        // Optional existing-bank-stock mode is deliberately lazy. A negative bank lookup
        // is cached for the rest of the session so PREPARING_STOCK does not keep opening
        // the bank before every GE basket. If bank-stock mode is disabled, this path never
        // opens the bank at all.
        int selectedItemId = activeOpportunity.getItemId();
        if (config.useBankStockFirst() && !bankStockExhaustedItemIds.contains(selectedItemId)) {
            if (!openBankSafely()) {
                status = "Opening bank once to check existing alch stock";
                return;
            }

            boolean hasStock = Rs2Bank.hasItem(selectedItemId);
            Rs2Bank.closeBank();
            if (hasStock) {
                committedItemId = selectedItemId;
                committedStockRemaining = 1; // placeholder; replaced after Withdraw-All
                state = State.PREPARING_ALCH;
                status = "Using existing bank stock";
                return;
            }

            bankStockExhaustedItemIds.add(selectedItemId);
        }

        // Final guard: never create a GE basket while a supported profitable item
        // is already available in inventory. This protects against inventory updates
        // landing between PREPARING_SUPPLIES and this state.
        if (activateProfitableInventoryStock()) {
            state = State.PREPARING_ALCH;
            status = "Inventory stock found before GE offers: " + activeOpportunity.getItemName();
            return;
        }

        planPurchaseBasket();
    }

    private void planPurchaseBasket() {
        if (refreshMembersContentMode()) {
            state = State.SCANNING_MARKET;
            status = "Membership/world mode changed - refreshing market";
            return;
        }
        // Do not spend time opening the bank/GE if an alchable inventory batch became
        // available since the previous state tick. Inventory takes strict priority.
        if (activateProfitableInventoryStock()) {
            state = State.PREPARING_ALCH;
            status = "Alching inventory before setting offers: " + activeOpportunity.getItemName();
            return;
        }

        // Do not open the bank just to top inventory up to Max spend / cycle. If we
        // already have spendable coins, build the basket from those coins. This prevents
        // a bank trip after every collected/alched batch merely because inventory capital
        // is below the configured maximum cycle budget.
        if (!ensureTradingCapital()) {
            return;
        }

        long spendableCoins = Math.max(0L, (long) Rs2Inventory.itemQuantity(COINS_ID) - config.reserveCoins());
        long remainingBudget = Math.min((long) config.maxSpendPerCycle(), spendableCoins);
        if (remainingBudget <= 0L) {
            state = State.ERROR;
            status = "Insufficient capital after coin reserve";
            return;
        }

        if (!ensureGrandExchangeOpen()) {
            return;
        }

        GrandExchangeSlots[] availableSlots = Rs2GrandExchange.getAvailableSlots();
        if (availableSlots.length == 0) {
            status = "No free GE slots";
            return;
        }

        pendingBasket.clear();
        basketPlacementIndex = 0;
        int slotsRemaining = availableSlots.length;

        for (AlchOpportunity ranked : new ArrayList<>(rankedOpportunities)) {
            if (slotsRemaining <= 0 || remainingBudget <= 0L) {
                break;
            }
            if (isSlowBuyCooldownActive(ranked.getItemId())
                    || blockedUntil.getOrDefault(ranked.getItemId(), 0L) > System.currentTimeMillis()) {
                continue;
            }
            // Do not collide with an offer the player already has for the same item.
            if (Rs2GrandExchange.findSlotForItem(ranked.getItemId(), false) != null) {
                continue;
            }

            AlchOpportunity refreshed = refreshOpportunity(ranked.getItemId());
            if (refreshed == null) {
                continue;
            }
            refreshed = withTradeLimit(refreshed);
            if (refreshed == null) {
                continue;
            }

            int remainingLimit = getRemainingTradeLimit(refreshed);
            if (remainingLimit <= 0) {
                blockItemUntil(refreshed.getItemId(),
                        getPurchaseWindowEnd(refreshed.getItemId(), System.currentTimeMillis()));
                continue;
            }

            int maxSafeBuyPrice = refreshed.getHighAlchValue()
                    - refreshed.getNatureRunePrice()
                    - refreshed.getFireRuneCost()
                    - config.minimumProfitPerCast();
            int requestedPrice = (int) Math.ceil(refreshed.getInstantBuyPrice()
                    * (1.0 + config.buyPriceBufferPercent() / 100.0));
            int buyPrice = Math.min(maxSafeBuyPrice, requestedPrice);
            if (buyPrice <= 0 || buyPrice < refreshed.getInstantBuyPrice()) {
                continue;
            }

            // Split the remaining basket budget across the remaining free slots. If an
            // item hits its quantity/GE limit early, the unused budget naturally flows
            // into subsequent offers.
            if (remainingBudget < buyPrice) {
                continue;
            }
            long targetBudgetForSlot = remainingBudget / Math.max(1, slotsRemaining);
            if (targetBudgetForSlot < buyPrice) {
                targetBudgetForSlot = buyPrice;
            }
            targetBudgetForSlot = Math.min(targetBudgetForSlot, remainingBudget);
            int byBudget = (int) Math.min(Integer.MAX_VALUE, targetBudgetForSlot / buyPrice);
            int quantity = Math.min(config.maxQuantityPerCycle(), byBudget);
            quantity = Math.min(quantity, remainingLimit);
            if (quantity <= 0) {
                continue;
            }

            pendingBasket.add(new BasketOffer(refreshed, quantity, buyPrice));
            remainingBudget -= (long) quantity * buyPrice;
            slotsRemaining--;
        }

        if (pendingBasket.isEmpty()) {
            state = State.NO_PROFITABLE_ITEM;
            status = "No basket candidate fits budget/limits";
            return;
        }

        activeOpportunity = pendingBasket.get(0).opportunity;
        state = State.PLACING_BASKET;
        status = "Placing " + pendingBasket.size() + " GE buy offers";
    }

    private void placeBasketOffers() {
        if (!ensureGrandExchangeOpen()) {
            return;
        }

        if (basketPlacementIndex >= pendingBasket.size()) {
            state = State.WAITING_FOR_BASKET;
            status = "Waiting for " + pendingBasket.size() + " GE offers";
            return;
        }

        BasketOffer offer = pendingBasket.get(basketPlacementIndex);
        if (offer.slot != null) {
            basketPlacementIndex++;
            return;
        }

        if (Rs2GrandExchange.getAvailableSlots().length == 0) {
            state = State.WAITING_FOR_BASKET;
            status = "All available GE slots filled";
            return;
        }

        status = "GE " + (basketPlacementIndex + 1) + "/" + pendingBasket.size()
                + ": " + offer.opportunity.getItemName();

        // The supplied Flipper JAR does not trust a UI click by itself; it resolves
        // the resulting live GE state before advancing. Do the same here so a failed
        // search/confirm does not make us lose track of a basket slot.
        GrandExchangeSlots placedSlot = geInteraction.placeBuyOffer(
                offer.opportunity.getItemName(),
                offer.opportunity.getItemId(),
                offer.price,
                offer.quantity);
        if (placedSlot == null) {
            status = "GE offer did not reach a live slot: " + offer.opportunity.getItemName();
            return;
        }

        offer.slot = placedSlot;
        offer.startedAt = System.currentTimeMillis();
        basketPlacementIndex++;

        if (basketPlacementIndex >= pendingBasket.size()) {
            state = State.WAITING_FOR_BASKET;
        }
    }

    private void waitForBasketOffers() {
        if (pendingBasket.isEmpty()) {
            finishBasketCycle();
            return;
        }

        // Alching is intentionally performed with the GE interface closed. Once that
        // collected stack is exhausted, reopen the GE overview before collecting/checking
        // the remaining basket offers. The offers themselves keep filling while the UI is closed.
        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Rs2GrandExchange.backToOverview();
            status = "Returning to GE overview";
            return;
        }
        if (!Rs2GrandExchange.isOpen()) {
            ensureGrandExchangeOpen();
            return;
        }

        // Use the GE overview Collect button rather than opening completed offer slots
        // individually. Wait until every offer that is currently complete has passed its
        // own reaction timer, then collect all completed offers to inventory in one click.
        // Active BUYING offers remain untouched and continue filling in the background.
        if (collectCompletedBasketOffersFromOverview()) {
            return;
        }

        long timeoutMs = TimeUnit.SECONDS.toMillis(config.offerTimeoutSeconds());
        for (BasketOffer offer : pendingBasket) {
            if (offer.resolved || offer.startedAt <= 0L || offer.aborted) continue;
            if (System.currentTimeMillis() - offer.startedAt < timeoutMs) continue;

            if (offer.slot == null) {
                offer.slot = Rs2GrandExchange.findSlotForItem(offer.opportunity.getItemId(), false);
                if (offer.slot == null) {
                    status = "Waiting for timeout slot: " + offer.opportunity.getItemName();
                    return;
                }
            }

            GrandExchangeOfferState offerState = getOfferState(offer.slot);
            if (offerState == GrandExchangeOfferState.BOUGHT) {
                status = "Completed during timeout - waiting for GE Collect";
                collectCompletedBasketOffersFromOverview();
                return;
            }
            if (offerState == GrandExchangeOfferState.CANCELLED_BUY) {
                applySlowBuyCooldown(offer.opportunity.getItemId(), offer.opportunity.getItemName());
                offer.aborted = true;
                offer.boughtAtAbort = Math.max(0, Rs2GrandExchange.getItemsBoughtFromOffer(offer.slot));
                offer.abortCollectReadyAt = System.currentTimeMillis() + randomAbortCollectDelay();
                status = "Slow buy cooldown: " + offer.opportunity.getItemName()
                        + " - Collect delayed " + formatRemaining(offer.abortCollectReadyAt);
                return;
            }

            int bought = Math.max(0, Rs2GrandExchange.getItemsBoughtFromOffer(offer.slot));
            if (!abortOfferWithoutCollect(offer.slot)) {
                status = "Retrying timeout abort: " + offer.opportunity.getItemName();
                return;
            }

            applySlowBuyCooldown(offer.opportunity.getItemId(), offer.opportunity.getItemName());
            offer.aborted = true;
            offer.boughtAtAbort = bought;
            offer.abortCollectReadyAt = System.currentTimeMillis() + randomAbortCollectDelay();
            status = "Timed out " + offer.opportunity.getItemName() + " - cooldown "
                    + formatSlowBuyCooldown(offer.opportunity.getItemId())
                    + ", Collect " + formatRemaining(offer.abortCollectReadyAt);
            return;
        }

        int resolved = 0;
        int filled = 0;
        int requested = 0;
        for (BasketOffer offer : pendingBasket) {
            if (offer.resolved) resolved++;
            if (offer.slot != null) filled += Math.max(0, Rs2GrandExchange.getItemsBoughtFromOffer(offer.slot));
            requested += offer.quantity;
        }
        status = "GE basket " + resolved + "/" + pendingBasket.size()
                + " resolved (" + filled + "/" + requested + ")";

        if (resolved >= pendingBasket.size()) {
            finishBasketCycle();
        }
    }

    /**
     * Collect all currently completed basket offers through the GE overview Collect
     * button. The button can collect more than one completed offer at once, so inventory
     * deltas are measured per basket item before/after the click and every collected
     * item is recorded into the alch queue.
     *
     * @return true when this tick is consumed by waiting for collection, collecting,
     *         or transitioning into alching; false when there are no completed offers.
     */
    private boolean collectCompletedBasketOffersFromOverview() {
        List<BasketOffer> collectableOffers = new ArrayList<>();
        long now = System.currentTimeMillis();
        long longestRemaining = 0L;

        for (BasketOffer offer : pendingBasket) {
            if (offer.resolved) continue;
            if (offer.slot == null) {
                offer.slot = Rs2GrandExchange.findSlotForItem(offer.opportunity.getItemId(), false);
                continue;
            }

            GrandExchangeOfferState offerState = getOfferState(offer.slot);
            long readyAt;
            if (offerState == GrandExchangeOfferState.BOUGHT) {
                if (offer.collectReadyAt <= 0L) {
                    offer.collectReadyAt = now + randomCompletedOfferReactionDelay();
                }
                readyAt = offer.collectReadyAt;
            } else if (offerState == GrandExchangeOfferState.CANCELLED_BUY || offer.aborted) {
                offer.aborted = true;
                if (offer.boughtAtAbort <= 0) {
                    offer.boughtAtAbort = Math.max(0, Rs2GrandExchange.getItemsBoughtFromOffer(offer.slot));
                }
                if (offer.abortCollectReadyAt <= 0L) {
                    offer.abortCollectReadyAt = now + randomAbortCollectDelay();
                }
                readyAt = offer.abortCollectReadyAt;
            } else {
                continue;
            }

            collectableOffers.add(offer);
            longestRemaining = Math.max(longestRemaining, readyAt - now);
        }

        if (collectableOffers.isEmpty()) {
            return false;
        }

        // The GE overview Collect button collects every completed/cancelled offer at
        // once. Wait until all offers that button would currently collect have satisfied
        // their own timer. Aborted offers use the separate abort min/max delay.
        if (longestRemaining > 0L) {
            long abortedCount = collectableOffers.stream().filter(x -> x.aborted).count();
            status = collectableOffers.size() + " GE offer" + (collectableOffers.size() == 1 ? "" : "s")
                    + " collectable" + (abortedCount > 0 ? " (" + abortedCount + " aborted)" : "")
                    + " - Collect in " + String.format("%.1fs", longestRemaining / 1000.0);
            return true;
        }

        Map<BasketOffer, Integer> expectedByOffer = new HashMap<>();
        Map<BasketOffer, Integer> inventoryBeforeByOffer = new HashMap<>();
        for (BasketOffer offer : collectableOffers) {
            inventoryBeforeByOffer.put(offer,
                    getInventoryQuantityIncludingNotes(offer.opportunity.getItemId()));
            int expected = offer.aborted
                    ? offer.boughtAtAbort
                    : Math.max(0, Rs2GrandExchange.getItemsBoughtFromOffer(offer.slot));
            expectedByOffer.put(offer, expected);
        }

        status = "Using GE Collect button for " + collectableOffers.size() + " offer"
                + (collectableOffers.size() == 1 ? "" : "s");
        if (!geInteraction.collectOverviewToInventory()) {
            status = "Retrying GE Collect button";
            return true;
        }

        sleepUntil(() -> {
            for (BasketOffer offer : collectableOffers) {
                int before = inventoryBeforeByOffer.getOrDefault(offer, 0);
                if (getInventoryQuantityIncludingNotes(offer.opportunity.getItemId()) > before) {
                    return true;
                }
                if (offer.slot != null && Rs2GrandExchange.isSlotAvailable(offer.slot)) {
                    return true;
                }
            }
            return false;
        }, 3000);

        BasketOffer nextToAlch = null;
        int nextCollectedQuantity = 0;
        for (BasketOffer offer : collectableOffers) {
            if (offer.resolved) continue;

            int before = inventoryBeforeByOffer.getOrDefault(offer, 0);
            int after = getInventoryQuantityIncludingNotes(offer.opportunity.getItemId());
            int delta = Math.max(0, after - before);
            int expected = expectedByOffer.getOrDefault(offer, 0);
            boolean slotCleared = offer.slot != null && Rs2GrandExchange.isSlotAvailable(offer.slot);

            int collected = delta > 0 ? delta : (slotCleared ? expected : 0);
            if (collected > 0) {
                recordBasketResult(offer, collected);
            } else if (slotCleared) {
                // Zero-fill aborts still need to be resolved after the refunded coins
                // have been collected from the overview button.
                recordBasketResult(offer, 0);
            } else {
                continue;
            }

            offer.resolved = true;
            if (collected > 0 && (nextToAlch == null
                    || offer.opportunity.getProfitPerCast() > nextToAlch.opportunity.getProfitPerCast())) {
                nextToAlch = offer;
                nextCollectedQuantity = collected;
            }
        }

        if (nextToAlch != null) {
            return beginAlchingCollectedBasketStock(
                    nextToAlch.opportunity.getItemId(),
                    "Collected via GE button: " + nextCollectedQuantity + " x "
                            + nextToAlch.opportunity.getItemName());
        }

        status = "GE Collect clicked - waiting for inventory update";
        return true;
    }

    private int getInventoryQuantityIncludingNotes(int baseItemId) {
        int notedItemId = getNotedItemId(baseItemId);
        int quantity = Rs2Inventory.itemQuantity(baseItemId);
        if (notedItemId > 0 && notedItemId != baseItemId) {
            quantity += Rs2Inventory.itemQuantity(notedItemId);
        }
        return quantity;
    }

    private int getPendingInventoryQuantity() {
        if (pendingPurchaseType == PurchaseType.ALCHABLE) {
            return getInventoryQuantityIncludingNotes(pendingItemId);
        }
        return Rs2Inventory.itemQuantity(pendingItemId);
    }

    /**
     * Switch from GE waiting into immediate alching as soon as one basket offer has
     * produced usable stock. Active GE offers are not cancelled; only the interface
     * is closed, so they continue filling while this stack is alched.
     */
    private boolean beginAlchingCollectedBasketStock(int collectedItemId, String collectedStatus) {
        AlchOpportunity collectedOpportunity = queuedOpportunityByItem.get(collectedItemId);
        int collectedStock = queuedStockByItem.getOrDefault(collectedItemId, 0);
        if (collectedOpportunity == null || collectedStock <= 0) {
            return false;
        }

        // Alch the exact item that was just collected. Choosing another queued item
        // here can make PREPARING_ALCH look in the bank for stock that is not in the
        // inventory, which caused an unnecessary bank open after GE collection.
        committedItemId = collectedItemId;
        committedStockRemaining = collectedStock;
        activeOpportunity = collectedOpportunity;

        if (!closeGrandExchangeInterface()) {
            state = State.PREPARING_ALCH;
            status = "Closing Grand Exchange before alch";
            return true;
        }
        state = State.PREPARING_ALCH;
        int remainingOffers = getUnresolvedBasketOfferCount();
        status = collectedStatus + " - alching while " + remainingOffers + " GE offer"
                + (remainingOffers == 1 ? " fills" : "s fill");
        return true;
    }

    private void recordBasketResult(BasketOffer offer, int quantityBought) {
        int itemId = offer.opportunity.getItemId();
        if (quantityBought > 0) {
            long now = System.currentTimeMillis();
            purchaseWindowStartedAtByItem.putIfAbsent(itemId, now);
            purchasedInWindow.merge(itemId, quantityBought, Integer::sum);
            AlchOpportunity locked = createConservativePurchasedOpportunity(offer.opportunity, offer.price);
            queuedStockByItem.merge(itemId, quantityBought, Integer::sum);
            queuedOpportunityByItem.put(itemId, locked);
            inventoryCollectedAtByItem.put(itemId, now);
        } else {
            blockItemUntil(itemId, System.currentTimeMillis() + ZERO_FILL_BLOCK_MS);
        }
    }

    private void finishBasketCycle() {
        pendingBasket.clear();
        basketPlacementIndex = 0;
        if (activateNextQueuedStock()) {
            state = State.PREPARING_ALCH;
            status = "Basket collected - preparing alch";
        } else {
            state = State.SCANNING_MARKET;
            status = "Basket had no fills - rescanning";
        }
    }

    /**
     * Finds profitable supported alchables that are already in inventory and makes
     * them the current batch before any new GE basket is created. Both noted and
     * unnoted forms are counted. The candidate must be present in the current market
     * ranking/queue so arbitrary inventory items are never alched.
     */
    private boolean activateProfitableInventoryStock() {
        if (committedItemId > 0 && committedStockRemaining > 0 && hasActiveAlchableInInventory()) {
            return true;
        }

        AlchOpportunity bestInventoryOpportunity = null;
        int bestInventoryQuantity = 0;

        // Prefer the latest locked opportunity for collected stock when available.
        for (Map.Entry<Integer, AlchOpportunity> entry : queuedOpportunityByItem.entrySet()) {
            AlchOpportunity opportunity = entry.getValue();
            if (opportunity == null) {
                continue;
            }
            int quantity = getInventoryQuantityIncludingNotes(entry.getKey());
            if (quantity <= 0) {
                continue;
            }
            if (bestInventoryOpportunity == null
                    || opportunity.getProfitPerCast() > bestInventoryOpportunity.getProfitPerCast()) {
                bestInventoryOpportunity = opportunity;
                bestInventoryQuantity = quantity;
            }
        }

        // Also catch supported/profitable items that were already in inventory before
        // the plugin began setting offers and therefore never entered queuedStockByItem.
        for (AlchOpportunity opportunity : new ArrayList<>(rankedOpportunities)) {
            if (opportunity == null) {
                continue;
            }
            int quantity = getInventoryQuantityIncludingNotes(opportunity.getItemId());
            if (quantity <= 0) {
                continue;
            }
            if (bestInventoryOpportunity == null
                    || opportunity.getProfitPerCast() > bestInventoryOpportunity.getProfitPerCast()) {
                bestInventoryOpportunity = opportunity;
                bestInventoryQuantity = quantity;
            }
        }

        // activeOpportunity may have been conservatively locked to the actual GE bid
        // and can therefore be more accurate than the raw market ranking.
        if (activeOpportunity != null) {
            int quantity = getInventoryQuantityIncludingNotes(activeOpportunity.getItemId());
            if (quantity > 0 && (bestInventoryOpportunity == null
                    || activeOpportunity.getProfitPerCast() >= bestInventoryOpportunity.getProfitPerCast())) {
                bestInventoryOpportunity = activeOpportunity;
                bestInventoryQuantity = quantity;
            }
        }

        if (bestInventoryOpportunity == null || bestInventoryQuantity <= 0) {
            return false;
        }

        int itemId = bestInventoryOpportunity.getItemId();
        activeOpportunity = bestInventoryOpportunity;
        committedItemId = itemId;
        committedStockRemaining = bestInventoryQuantity;
        queuedStockByItem.put(itemId, bestInventoryQuantity);
        queuedOpportunityByItem.put(itemId, bestInventoryOpportunity);
        if (cleanupItemId == itemId) {
            cleanupItemId = 0;
        }
        failedCastAttempts = 0;
        return true;
    }

    private boolean activateNextQueuedStock() {
        if (committedStockRemaining > 0 && committedItemId > 0) {
            return true;
        }

        int nextItemId = queuedStockByItem.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .max(Comparator.comparingInt(e -> {
                    AlchOpportunity opp = queuedOpportunityByItem.get(e.getKey());
                    return opp == null ? Integer.MIN_VALUE : opp.getProfitPerCast();
                }))
                .map(Map.Entry::getKey)
                .orElse(0);

        if (nextItemId <= 0) {
            return false;
        }

        AlchOpportunity next = queuedOpportunityByItem.get(nextItemId);
        if (next == null) {
            queuedStockByItem.remove(nextItemId);
            return false;
        }

        committedItemId = nextItemId;
        committedStockRemaining = queuedStockByItem.getOrDefault(nextItemId, 0);
        activeOpportunity = next;
        return committedStockRemaining > 0;
    }

    private void cleanupFinishedStock() {
        if (cleanupItemId <= 0) return;
        int baseId = cleanupItemId;
        int notedId = getNotedItemId(baseId);
        if (!Rs2Inventory.hasItem(baseId) && !Rs2Inventory.hasItem(notedId)) {
            cleanupItemId = 0;
            return;
        }
        if (!openBankSafely()) {
            status = "Banking leftover noted stock";
            return;
        }
        if (Rs2Inventory.hasItem(notedId)) {
            Rs2Bank.depositAll(notedId);
        }
        if (baseId != notedId && Rs2Inventory.hasItem(baseId)) {
            Rs2Bank.depositAll(baseId);
        }
        Rs2Bank.closeBank();
        cleanupItemId = 0;
    }

    private AlchOpportunity refreshOpportunity(int itemId) {
        ItemComposition composition = getItemComposition(itemId);
        WikiPrice itemPrice = Rs2GrandExchange.getRealTimePrices(itemId);
        WikiPrice naturePrice = Rs2GrandExchange.getRealTimePrices(NATURE_RUNE_ID);
        if (composition == null || (composition.isMembers() && !membersContentEnabled) || !composition.isGeTradeable() ||
                itemPrice == null || naturePrice == null || itemPrice.buyPrice <= 0 || naturePrice.buyPrice <= 0) {
            return null;
        }
        if (itemPrice.volume > 0 && itemPrice.volume < config.minimumVolume()) {
            return null;
        }

        int fireCost = 0;
        if (!usingFireStaff) {
            WikiPrice firePrice = Rs2GrandExchange.getRealTimePrices(FIRE_RUNE_ID);
            if (firePrice == null || firePrice.buyPrice <= 0) {
                return null;
            }
            fireCost = conservativeRuneBuyPrice(firePrice.buyPrice) * 5;
        }

        int conservativeNaturePrice = conservativeRuneBuyPrice(naturePrice.buyPrice);
        int profit = composition.getHaPrice() - itemPrice.buyPrice - conservativeNaturePrice - fireCost;
        long gpHour = (long) profit * HIGH_ALCH_CASTS_PER_HOUR;
        if (profit < config.minimumProfitPerCast() || gpHour < config.minimumExpectedGpPerHour()) {
            return null;
        }

        return new AlchOpportunity(
                itemId,
                composition.getMembersName(),
                itemPrice.buyPrice,
                composition.getHaPrice(),
                conservativeNaturePrice,
                fireCost,
                profit,
                gpHour,
                itemPrice.volume,
                -1,
                System.currentTimeMillis()
        );
    }

    private void preparePendingPurchase(PurchaseType type, int itemId, String itemName, int quantity, int price) {
        pendingPurchaseType = type;
        pendingItemId = itemId;
        pendingItemName = itemName;
        pendingQuantity = quantity;
        pendingPrice = price;
        pendingSlot = null;
        pendingOfferStartedAt = 0L;

        long required = (long) quantity * price;
        long maxRequired = Math.min(Integer.MAX_VALUE, required + config.reserveCoins());
        if (!ensureCoinsInInventory((int) maxRequired)) {
            // openBankSafely can legitimately fail for a tick while another interface is
            // closing. Do not permanently ERROR on that transient condition.
            clearPendingPurchase();
            state = State.PREPARING_SUPPLIES;
            status = "Waiting for trading capital";
            return;
        }

        // If the account has less capital than the configured cycle budget, automatically
        // scale the order down while preserving the configured coin reserve.
        long spendableCoins = Math.max(0L, (long) Rs2Inventory.itemQuantity(COINS_ID) - config.reserveCoins());
        int affordableQuantity = (int) Math.min(Integer.MAX_VALUE, spendableCoins / Math.max(1, price));
        pendingQuantity = Math.min(pendingQuantity, affordableQuantity);

        if (pendingQuantity <= 0) {
            clearPendingPurchase();
            state = State.ERROR;
            status = "Insufficient capital after coin reserve";
            return;
        }

        state = State.PLACING_BUY;
    }

    private boolean ensureCoinsInInventory(int targetCoins) {
        int currentCoins = Rs2Inventory.itemQuantity(COINS_ID);
        if (currentCoins >= targetCoins) {
            return true;
        }

        // If a previous bank check already proved there are no coins there, do not
        // reopen the bank on every PREPARING_STOCK pass.
        if (bankCoinsExhausted) {
            return currentCoins > config.reserveCoins();
        }

        if (!openBankSafely()) {
            status = "Opening bank for coins";
            return false;
        }

        // Withdraw the available coin stack rather than demanding an exact deficit. The
        // caller can scale its order to whatever capital is actually available.
        if (Rs2Bank.hasItem(COINS_ID)) {
            int before = Rs2Inventory.itemQuantity(COINS_ID);
            Rs2Bank.withdrawAll(COINS_ID);
            sleepUntil(() -> Rs2Inventory.itemQuantity(COINS_ID) > before, 3000);
        } else {
            bankCoinsExhausted = true;
        }
        Rs2Bank.closeBank();
        return Rs2Inventory.itemQuantity(COINS_ID) > config.reserveCoins();
    }

    /**
     * Basket cycles use the coins already in inventory whenever possible. Banking is
     * only needed when the inventory has no spendable capital after the configured
     * reserve. This is intentionally different from trying to reach maxSpendPerCycle.
     */
    private boolean ensureTradingCapital() {
        if (Rs2Inventory.itemQuantity(COINS_ID) > config.reserveCoins()) {
            return true;
        }
        return ensureCoinsInInventory(config.reserveCoins() + 1);
    }

    private void placePendingBuy() {
        if (pendingItemName == null || pendingQuantity <= 0 || pendingPrice <= 0) {
            state = State.ERROR;
            status = "Invalid pending purchase";
            return;
        }

        long required = (long) pendingQuantity * pendingPrice;
        long requiredWithReserve = required + config.reserveCoins();
        if (Rs2Inventory.itemQuantity(COINS_ID) < requiredWithReserve) {
            state = State.ERROR;
            status = "Not enough coins after reserve for " + pendingItemName;
            return;
        }

        if (!ensureGrandExchangeOpen()) {
            return;
        }

        status = "Buying " + pendingQuantity + " x " + pendingItemName;
        GrandExchangeSlots placedSlot = geInteraction.placeBuyOffer(
                pendingItemName, pendingItemId, pendingPrice, pendingQuantity);
        if (placedSlot == null) {
            status = "GE offer did not reach a live slot: " + pendingItemName;
            return;
        }

        pendingSlot = placedSlot;
        pendingOfferStartedAt = System.currentTimeMillis();
        state = State.WAITING_FOR_BUY;
    }

    private void waitForPendingBuy() {
        if (pendingSlot == null) {
            pendingSlot = Rs2GrandExchange.findSlotForItem(pendingItemId, false);
            if (pendingSlot == null && pendingItemName != null) {
                pendingSlot = Rs2GrandExchange.findSlotForItem(pendingItemName, false);
            }
            if (pendingSlot == null) {
                status = "Waiting for GE slot: " + pendingItemName;
                return;
            }
        }

        GrandExchangeOfferState offerState = getOfferState(pendingSlot);
        long elapsed = System.currentTimeMillis() - pendingOfferStartedAt;
        long offerTimeoutMs = TimeUnit.SECONDS.toMillis(config.offerTimeoutSeconds());
        if (offerState == GrandExchangeOfferState.CANCELLED_BUY && !pendingAborted) {
            if (pendingPurchaseType == PurchaseType.ALCHABLE && elapsed >= offerTimeoutMs) {
                applySlowBuyCooldown(pendingItemId, pendingItemName);
            }
            pendingAborted = true;
            pendingBoughtAtAbort = Math.max(0, Rs2GrandExchange.getItemsBoughtFromOffer(pendingSlot));
            pendingAbortCollectReadyAt = System.currentTimeMillis() + randomAbortCollectDelay();
        }

        if (pendingAborted) {
            long now = System.currentTimeMillis();
            if (pendingAbortCollectReadyAt <= 0L) {
                pendingAbortCollectReadyAt = now + randomAbortCollectDelay();
            }
            if (now < pendingAbortCollectReadyAt) {
                status = "Aborted " + pendingItemName + " - Collect in "
                        + String.format("%.1fs", (pendingAbortCollectReadyAt - now) / 1000.0);
                return;
            }

            int beforeQuantity = getPendingInventoryQuantity();
            status = "Collecting aborted offer: " + pendingItemName;
            if (!geInteraction.collectOverviewToInventory()) {
                status = "Retrying aborted-offer Collect";
                return;
            }
            sleepUntil(() -> getPendingInventoryQuantity() > beforeQuantity
                    || Rs2GrandExchange.isSlotAvailable(pendingSlot), 3000);
            int inventoryDelta = Math.max(0, getPendingInventoryQuantity() - beforeQuantity);
            int bought = inventoryDelta > 0 ? inventoryDelta : pendingBoughtAtAbort;
            finishPendingPurchase(bought);
            return;
        }

        if (offerState == GrandExchangeOfferState.BOUGHT) {
            if (!isPendingCollectionReactionReady()) {
                return;
            }

            int expectedBought = Rs2GrandExchange.getItemsBoughtFromOffer(pendingSlot);
            int beforeQuantity = getPendingInventoryQuantity();
            status = "Using GE Collect button: " + pendingItemName;
            boolean collected = geInteraction.collectOverviewToInventory();
            if (!collected) {
                status = "Retrying GE Collect button";
                return;
            }

            sleepUntil(() -> getPendingInventoryQuantity() > beforeQuantity
                    || Rs2GrandExchange.isSlotAvailable(pendingSlot), 3000);
            int inventoryDelta = Math.max(0, getPendingInventoryQuantity() - beforeQuantity);
            int bought = inventoryDelta > 0 ? inventoryDelta : expectedBought;
            finishPendingPurchase(bought);
            return;
        }

        if (elapsed >= offerTimeoutMs) {
            int bought = Math.max(0, Rs2GrandExchange.getItemsBoughtFromOffer(pendingSlot));
            if (!abortOfferWithoutCollect(pendingSlot)) {
                status = "Retrying timeout abort: " + pendingItemName;
                return;
            }

            if (pendingPurchaseType == PurchaseType.ALCHABLE) {
                applySlowBuyCooldown(pendingItemId, pendingItemName);
            }
            pendingAborted = true;
            pendingBoughtAtAbort = bought;
            pendingAbortCollectReadyAt = System.currentTimeMillis() + randomAbortCollectDelay();
            status = "Aborted " + pendingItemName
                    + (pendingPurchaseType == PurchaseType.ALCHABLE
                    ? " - cooldown " + formatSlowBuyCooldown(pendingItemId) : "")
                    + " - Collect delayed " + formatRemaining(pendingAbortCollectReadyAt);
            return;
        }

        int boughtSoFar = Rs2GrandExchange.getItemsBoughtFromOffer(pendingSlot);
        status = "Waiting GE: " + boughtSoFar + "/" + pendingQuantity + " " + pendingItemName;
    }

    /**
     * Single-offer/rune purchases use the same stable reaction timer before the
     * GE overview Collect button is clicked. Basket offers manage their timers
     * together because one overview Collect click can collect several slots.
     */
    private boolean isPendingCollectionReactionReady() {
        long now = System.currentTimeMillis();
        if (pendingCollectReadyAt <= 0L) {
            pendingCollectReadyAt = now + randomCompletedOfferReactionDelay();
        }
        if (now < pendingCollectReadyAt) {
            long remaining = pendingCollectReadyAt - now;
            status = "Offer filled: " + pendingItemName
                    + " - collecting in " + String.format("%.1fs", Math.max(1L, remaining) / 1000.0);
            return false;
        }
        return true;
    }

    private long randomCompletedOfferReactionDelay() {
        long minMs = TimeUnit.SECONDS.toMillis(Math.max(1, config.minimumCollectDelaySeconds()));
        long maxMs = TimeUnit.SECONDS.toMillis(Math.max(config.minimumCollectDelaySeconds(),
                config.maximumCollectDelaySeconds()));
        return ThreadLocalRandom.current().nextLong(minMs, maxMs + 1L);
    }

    private long randomAbortCollectDelay() {
        long minMs = TimeUnit.SECONDS.toMillis(Math.max(1, config.minimumAbortCollectDelaySeconds()));
        long maxMs = TimeUnit.SECONDS.toMillis(Math.max(config.minimumAbortCollectDelaySeconds(),
                config.maximumAbortCollectDelaySeconds()));
        return ThreadLocalRandom.current().nextLong(minMs, maxMs + 1L);
    }

    private String formatRemaining(long readyAt) {
        long remaining = Math.max(0L, readyAt - System.currentTimeMillis());
        return String.format("%.1fs", remaining / 1000.0);
    }

    private GrandExchangeOfferState getOfferState(GrandExchangeSlots slot) {
        if (slot == null) {
            return GrandExchangeOfferState.EMPTY;
        }
        GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
        return details == null || details.getState() == null
                ? GrandExchangeOfferState.EMPTY
                : details.getState();
    }

    /**
     * Abort a GE buy offer without collecting it. Rs2GrandExchange.abortOffer(...)
     * immediately calls collectAll(), which prevents a configurable post-abort reaction
     * delay. This mirrors Microbot's current Abort offer menu action but deliberately
     * leaves the cancelled offer/refund in the GE overview until our abort timer expires.
     */
    private boolean abortOfferWithoutCollect(GrandExchangeSlots slot) {
        if (slot == null || !ensureGrandExchangeOpen()) {
            return false;
        }

        GrandExchangeOfferState beforeState = getOfferState(slot);
        if (beforeState == GrandExchangeOfferState.CANCELLED_BUY) {
            return true;
        }
        if (beforeState == GrandExchangeOfferState.BOUGHT) {
            return false;
        }
        if (beforeState != GrandExchangeOfferState.BUYING) {
            return false;
        }

        // Mirror the supplied Flipper JAR's robust GE-slot handling. It opens the
        // real right-click menu on the live slot, waits for the same Abort action
        // to remain stable across multiple polls, verifies that action belongs to
        // this exact slot widget, and only then clicks the menu row.
        if (!geInteraction.abortOfferViaStableMenu(slot)) {
            return false;
        }

        sleepUntil(() -> {
            GrandExchangeOfferState stateNow = getOfferState(slot);
            return stateNow == GrandExchangeOfferState.CANCELLED_BUY
                    || stateNow == GrandExchangeOfferState.BOUGHT;
        }, 2500);

        // If the buy completed while the context menu was being opened/clicked,
        // let normal completed-offer collection handle it instead of marking it
        // as an aborted offer.
        return getOfferState(slot) == GrandExchangeOfferState.CANCELLED_BUY;
    }

    private void finishPendingPurchase(int quantityBought) {
        PurchaseType completedType = pendingPurchaseType;
        int completedItemId = pendingItemId;

        if (quantityBought > 0 && completedType == PurchaseType.ALCHABLE) {
            long now = System.currentTimeMillis();
            purchaseWindowStartedAtByItem.putIfAbsent(completedItemId, now);
            purchasedInWindow.merge(completedItemId, quantityBought, Integer::sum);
            inventoryCollectedAtByItem.put(completedItemId, now);
            lockConservativePurchasedMargin(pendingPrice);
            if (committedItemId == completedItemId || committedStockRemaining <= 0) {
                committedItemId = completedItemId;
                committedStockRemaining += quantityBought;
            }
        }

        if (quantityBought <= 0 && completedType == PurchaseType.ALCHABLE) {
            blockItemUntil(completedItemId, System.currentTimeMillis() + ZERO_FILL_BLOCK_MS);
        }

        clearPendingPurchase();

        if (completedType == PurchaseType.ALCHABLE) {
            if (quantityBought > 0) {
                state = State.PREPARING_ALCH;
                status = "Bought " + quantityBought + " - preparing alch";
            } else {
                state = State.SCANNING_MARKET;
                status = "No fill - trying another item";
            }
        } else {
            state = State.PREPARING_SUPPLIES;
            status = "Rune restock complete";
        }
    }


    /**
     * After an alchable GE purchase completes, use the submitted offer price as the unit-cost
     * ceiling for session profit tracking. GE can fill below this price, so this deliberately
     * understates rather than overstates the expected item margin.
     */
    private void lockConservativePurchasedMargin(int maxPaidPerItem) {
        if (activeOpportunity == null || maxPaidPerItem <= 0) {
            return;
        }
        activeOpportunity = createConservativePurchasedOpportunity(activeOpportunity, maxPaidPerItem);
    }

    private AlchOpportunity createConservativePurchasedOpportunity(
            AlchOpportunity opportunity, int maxPaidPerItem) {
        int profit = opportunity.getHighAlchValue()
                - maxPaidPerItem
                - opportunity.getNatureRunePrice()
                - opportunity.getFireRuneCost();

        return new AlchOpportunity(
                opportunity.getItemId(),
                opportunity.getItemName(),
                maxPaidPerItem,
                opportunity.getHighAlchValue(),
                opportunity.getNatureRunePrice(),
                opportunity.getFireRuneCost(),
                profit,
                (long) profit * HIGH_ALCH_CASTS_PER_HOUR,
                opportunity.getVolume(),
                opportunity.getTradeLimitPer4Hours(),
                System.currentTimeMillis()
        );
    }

    private void prepareAlchInventory() {
        // Re-check account/world access before touching a queued members-only item. A member
        // can hop to an F2P world (or membership can expire) while offers/inventory still exist.
        // In that case, stop the P2P handoff and rebuild the market in F2P mode instead of
        // repeatedly trying to alch or purchase an item that is unavailable in this world.
        refreshMembersContentMode();
        if (committedItemId > 0) {
            ItemComposition committedComposition = getItemComposition(committedItemId);
            if (committedComposition != null
                    && committedComposition.isMembers()
                    && !membersContentEnabled) {
                queuedStockByItem.remove(committedItemId);
                queuedOpportunityByItem.remove(committedItemId);
                committedItemId = 0;
                committedStockRemaining = 0;
                activeOpportunity = null;
                state = State.SCANNING_MARKET;
                status = "Members-only stock unavailable in current world";
                return;
            }
        }

        // Never enter the High Alchemy flow with the GE widget still open. Closing the
        // interface does not cancel active offers; they continue filling in the background.
        if (!closeGrandExchangeInterface()) {
            status = "Closing Grand Exchange before alch";
            return;
        }

        if (activeOpportunity == null || committedItemId <= 0 || committedStockRemaining <= 0) {
            if (activateNextQueuedStock()) {
                activeOpportunity = queuedOpportunityByItem.get(committedItemId);
            } else {
                state = State.PREPARING_STOCK;
                return;
            }
        }

        int baseItemId = committedItemId;
        int notedItemId = getNotedItemId(baseItemId);

        if (hasActiveAlchableInInventory()) {
            if (config.useBankStockFirst()) {
                committedStockRemaining = Rs2Inventory.itemQuantity(notedItemId)
                        + (baseItemId == notedItemId ? 0 : Rs2Inventory.itemQuantity(baseItemId));
            }
            failedCastAttempts = 0;
            state = State.ALCHING;
            status = "Alching noted " + activeOpportunity.getItemName();
            return;
        }

        // GE-bought alchables are collected directly into inventory. Never open the
        // bank merely because the inventory update lands one or two client ticks later.
        Long collectedAt = inventoryCollectedAtByItem.get(baseItemId);
        if (!config.useBankStockFirst() && collectedAt != null) {
            long age = System.currentTimeMillis() - collectedAt;
            if (age < 5000L) {
                status = "Waiting for collected notes: " + activeOpportunity.getItemName();
                return;
            }
            state = State.ERROR;
            status = "Collected stock missing from inventory: " + activeOpportunity.getItemName();
            return;
        }

        if (!openBankSafely()) {
            status = "Opening bank for alch stock";
            return;
        }

        if (!Rs2Bank.setWithdrawAsNote()) {
            status = "Setting bank withdraw-as-note mode";
            return;
        }

        // Keep coins/runes in inventory. Any stale item left from a prior completed
        // Withdraw-All batch is banked before the next selected item is withdrawn.
        Rs2Bank.depositAllExcept(COINS_ID, NATURE_RUNE_ID, FIRE_RUNE_ID);

        if (!Rs2Bank.hasItem(baseItemId)) {
            Rs2Bank.closeBank();
            queuedStockByItem.remove(baseItemId);
            queuedOpportunityByItem.remove(baseItemId);
            committedItemId = 0;
            committedStockRemaining = 0;
            state = State.PREPARING_STOCK;
            status = "Selected purchased stock missing from bank";
            return;
        }

        // Requested behavior: alchable items are always withdrawn as notes with
        // Withdraw-All. Tracking still prevents accidental extra alchs when
        // useBankStockFirst is disabled.
        boolean withdrew = Rs2Bank.withdrawAll(baseItemId);
        if (withdrew && config.useBankStockFirst()) {
            // Withdraw-All emptied the currently known bank stack. Do not probe the bank
            // again for this item on the next PREPARING_STOCK pass.
            bankStockExhaustedItemIds.add(baseItemId);
        }
        if (!withdrew) {
            Rs2Bank.closeBank();
            state = State.PREPARING_SUPPLIES;
            status = "Failed to Withdraw-All noted stock";
            return;
        }

        sleepUntil(() -> Rs2Inventory.itemQuantity(notedItemId) > 0
                || Rs2Inventory.itemQuantity(baseItemId) > 0, 3000);
        Rs2Bank.closeBank();

        if (!hasActiveAlchableInInventory()) {
            state = State.PREPARING_SUPPLIES;
            status = "Failed to withdraw noted alch stock";
            return;
        }

        if (config.useBankStockFirst()) {
            committedStockRemaining = Rs2Inventory.itemQuantity(notedItemId)
                    + (baseItemId == notedItemId ? 0 : Rs2Inventory.itemQuantity(baseItemId));
            queuedStockByItem.put(baseItemId, committedStockRemaining);
            queuedOpportunityByItem.put(baseItemId, activeOpportunity);
        }

        failedCastAttempts = 0;
        state = State.ALCHING;
        status = "Alching noted " + activeOpportunity.getItemName();
    }

    private void alchNextItem() {
        if (System.currentTimeMillis() < nextCastAllowedAt) {
            return;
        }

        if (activeOpportunity == null) {
            state = State.SCANNING_MARKET;
            return;
        }

        if (Rs2GrandExchange.isOpen() || Rs2GrandExchange.isOfferScreenOpen()) {
            state = State.PREPARING_ALCH;
            status = "Closing Grand Exchange before alch";
            return;
        }

        int baseItemId = activeOpportunity.getItemId();

        // Withdraw-All can place more notes in inventory than the plugin purchased.
        // Never alch beyond the tracked amount unless the user explicitly enabled bank-stock mode.
        if (committedStockRemaining <= 0) {
            finishCurrentAlchBatch(baseItemId);
            return;
        }

        Rs2ItemModel item = getActiveAlchableInventoryItem();
        if (item == null) {
            state = State.PREPARING_SUPPLIES;
            return;
        }
        if (Rs2Inventory.itemQuantity(NATURE_RUNE_ID) < 1) {
            state = State.PREPARING_SUPPLIES;
            return;
        }
        if (!usingFireStaff && Rs2Inventory.itemQuantity(FIRE_RUNE_ID) < 5) {
            state = State.PREPARING_SUPPLIES;
            return;
        }

        int inventoryItemId = item.getId();
        int before = Rs2Inventory.itemQuantity(inventoryItemId);
        long castStartedAt = System.currentTimeMillis();
        Rs2Magic.alch(item, 120, 220);

        if (activeOpportunity.getHighAlchValue() > Rs2Settings.getMinimumItemValueAlchemyWarning()) {
            sleepUntil(() -> Rs2Widget.hasWidget("Proceed to cast High Alchemy on it"), 1500);
            if (Rs2Widget.hasWidget("Proceed to cast High Alchemy on it")) {
                Rs2Keyboard.keyPress('1');
            }
        }

        boolean castCompleted = sleepUntil(() -> Rs2Inventory.itemQuantity(inventoryItemId) < before, 4500);
        if (castCompleted) {
            failedCastAttempts = 0;
            int jitter = config.customAntiban()
                    ? customAntiban.nextCastJitterMs(config.antibanProfile())
                    : 0;
            nextCastAllowedAt = castStartedAt + HIGH_ALCH_COOLDOWN_MS + jitter;
            castsCompleted++;
            projectedProfit += activeOpportunity.getProfitPerCast();

            if (committedItemId == baseItemId && committedStockRemaining > 0) {
                committedStockRemaining--;
                if (committedStockRemaining > 0) {
                    queuedStockByItem.put(baseItemId, committedStockRemaining);
                } else {
                    queuedStockByItem.remove(baseItemId);
                    queuedOpportunityByItem.remove(baseItemId);
                    cleanupItemId = baseItemId;
                    committedItemId = 0;
                }
            }

            if (config.customAntiban()) {
                customAntiban.afterSuccessfulCast(config.antibanProfile());
            }

            if (committedStockRemaining <= 0) {
                finishCurrentAlchBatch(baseItemId);
            } else {
                status = "Alching noted " + activeOpportunity.getItemName();
            }
        } else {
            failedCastAttempts++;
            status = "High Alch retry " + failedCastAttempts + "/3";
            if (failedCastAttempts >= 3) {
                state = State.PREPARING_SUPPLIES;
            }
        }
    }

    /**
     * Finish the currently collected alch stack. If other basket offers are still
     * unresolved, immediately return to GE monitoring instead of planning a new buy.
     */
    private void finishCurrentAlchBatch(int baseItemId) {
        cleanupItemId = baseItemId;
        queuedStockByItem.remove(baseItemId);
        queuedOpportunityByItem.remove(baseItemId);
        inventoryCollectedAtByItem.remove(baseItemId);
        committedItemId = 0;
        committedStockRemaining = 0;

        int remainingOffers = getUnresolvedBasketOfferCount();

        // The overview Collect button can collect several completed offers at once.
        // Drain every collected stack already in the queue before reopening the GE;
        // unresolved BUYING offers continue filling while these stacks are alched.
        if (activateNextQueuedStock()) {
            activeOpportunity = queuedOpportunityByItem.get(committedItemId);
            state = State.PREPARING_ALCH;
            status = "Next collected stack - " + remainingOffers + " GE offer"
                    + (remainingOffers == 1 ? "" : "s") + " still filling";
            return;
        }

        if (remainingOffers > 0) {
            state = State.WAITING_FOR_BASKET;
            status = "Collected stacks complete - checking " + remainingOffers + " remaining GE offer"
                    + (remainingOffers == 1 ? "" : "s");
            return;
        }

        // All basket offers are resolved. Clear the basket now; PREPARING_STOCK will
        // clean any accidental surplus notes and then continue with queued stock/new buys.
        if (!pendingBasket.isEmpty()) {
            pendingBasket.clear();
            basketPlacementIndex = 0;
        }
        state = State.PREPARING_STOCK;
        status = "Basket batch complete - continuing";
    }

    private int getUnresolvedBasketOfferCount() {
        return (int) pendingBasket.stream().filter(offer -> !offer.resolved).count();
    }

    /**
     * High Alchemy must never be attempted while the Grand Exchange widget is open.
     * Return from an offer detail screen to the overview first, then close the overview.
     * Active offers remain active after the interface is closed.
     */
    private boolean closeGrandExchangeInterface() {
        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 2000);
        }
        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2000);
        }
        return !Rs2GrandExchange.isOpen() && !Rs2GrandExchange.isOfferScreenOpen();
    }

    private boolean tryEquipFireStaff() {
        if (isUsingFireStaff()) {
            return true;
        }
        if (fireStaffUnavailable) {
            return false;
        }

        if (Rs2Inventory.contains(STAFF_OF_FIRE_ID)) {
            Rs2Inventory.interact(STAFF_OF_FIRE_ID, "Wield");
            sleepUntil(this::isUsingFireStaff, 2500);
            if (isUsingFireStaff()) return true;
        }
        if (!openBankSafely()) {
            return false;
        }

        int staffId = -1;
        if (Rs2Bank.hasItem(STAFF_OF_FIRE_ID)) {
            staffId = STAFF_OF_FIRE_ID;
        }

        if (staffId != -1) {
            // Equipment must be withdrawn unnoted even though alchables use note mode.
            Rs2Bank.setWithdrawAsItem();
            Rs2Bank.withdrawAndEquip(staffId);
        } else {
            fireStaffUnavailable = true;
        }
        Rs2Bank.closeBank();
        sleepUntil(this::isUsingFireStaff, 2500);
        return isUsingFireStaff();
    }

    private boolean isUsingFireStaff() {
        return Rs2Equipment.isWearing(STAFF_OF_FIRE_ID);
    }

    private int getNotedItemId(int baseItemId) {
        int notedId = Rs2ItemModel.getNotedId(baseItemId);
        return notedId > 0 ? notedId : baseItemId;
    }

    private Rs2ItemModel getActiveAlchableInventoryItem() {
        if (activeOpportunity == null) {
            return null;
        }

        int baseItemId = activeOpportunity.getItemId();
        int notedItemId = getNotedItemId(baseItemId);

        // Note mode is the normal path. The unnoted fallback keeps the script recoverable if an
        // item has no noted form or was manually placed in the inventory as an item.
        Rs2ItemModel noted = Rs2Inventory.get(notedItemId);
        if (noted != null) {
            return noted;
        }
        return Rs2Inventory.get(baseItemId);
    }

    private boolean hasActiveAlchableInInventory() {
        return getActiveAlchableInventoryItem() != null;
    }

    private int getAlchBatchSize() {
        // Reserve slots for coins and Nature runes. Reserve one more for Fire runes when no fire staff is used.
        return usingFireStaff ? 26 : 25;
    }

    private int getRemainingTradeLimit(AlchOpportunity opportunity) {
        if (!config.respectGeLimits() || opportunity.getTradeLimitPer4Hours() <= 0) {
            return Integer.MAX_VALUE;
        }
        int used = purchasedInWindow.getOrDefault(opportunity.getItemId(), 0);
        return Math.max(0, opportunity.getTradeLimitPer4Hours() - used);
    }

    private void resetExpiredPurchaseWindows() {
        long now = System.currentTimeMillis();
        List<Integer> expiredItems = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : purchaseWindowStartedAtByItem.entrySet()) {
            if (now - entry.getValue() >= FOUR_HOURS_MS) {
                expiredItems.add(entry.getKey());
            }
        }
        for (int itemId : expiredItems) {
            purchaseWindowStartedAtByItem.remove(itemId);
            purchasedInWindow.remove(itemId);
        }
        blockedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        slowBuyCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void blockItemUntil(int itemId, long until) {
        blockedUntil.merge(itemId, until, Math::max);
    }

    private void applySlowBuyCooldown(int itemId, String itemName) {
        if (itemId <= 0 || config == null) {
            return;
        }
        long until = System.currentTimeMillis()
                + TimeUnit.MINUTES.toMillis(Math.max(1, config.slowBuyCooldownMinutes()));
        slowBuyCooldownUntil.merge(itemId, until, Math::max);
        blockItemUntil(itemId, until);
    }

    private boolean isSlowBuyCooldownActive(int itemId) {
        return slowBuyCooldownUntil.getOrDefault(itemId, 0L) > System.currentTimeMillis();
    }

    private String formatSlowBuyCooldown(int itemId) {
        long until = slowBuyCooldownUntil.getOrDefault(itemId, 0L);
        if (until <= System.currentTimeMillis()) {
            return "ready";
        }
        long remaining = until - System.currentTimeMillis();
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60L;
        return minutes + "m " + seconds + "s";
    }

    private long getPurchaseWindowEnd(int itemId, long fallbackStart) {
        long started = purchaseWindowStartedAtByItem.getOrDefault(itemId, fallbackStart);
        return started + FOUR_HOURS_MS;
    }

    private boolean isMarketRefreshDue() {
        long interval = TimeUnit.MINUTES.toMillis(config.marketRefreshMinutes());
        return lastMarketScanAt == 0L || System.currentTimeMillis() - lastMarketScanAt >= interval;
    }

    private static int conservativeRuneBuyPrice(int liveInstantBuyPrice) {
        return (int) Math.ceil(liveInstantBuyPrice * RUNE_BUY_MULTIPLIER);
    }

    private int getMagicLevel() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getRealSkillLevel(Skill.MAGIC)
        ).orElse(0);
    }

    private ItemComposition getItemComposition(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getItemDefinition(itemId)
        ).orElse(null);
    }

    /**
     * Open the GE without hammering the walker every 600 ms. openExchange() is tried
     * first; walking is rate-limited and only started when the exchange cannot be
     * opened from the current location.
     */
    private boolean ensureGrandExchangeOpen() {
        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Rs2GrandExchange.backToOverview();
            status = "Returning to GE overview";
            return false;
        }
        if (Rs2GrandExchange.isOpen()) {
            nextGeTravelAttemptAt = 0L;
            return true;
        }
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            status = "Closing bank before Grand Exchange";
            return false;
        }

        // The supplied Flipper JAR rate-limits the entire GE acquisition attempt,
        // not only walking. That prevents openExchange()/walker calls from being
        // hammered by a 600 ms state-machine tick when the client UI is not ready.
        long now = System.currentTimeMillis();
        if (now < nextGeTravelAttemptAt) {
            status = "Waiting to retry Grand Exchange";
            return false;
        }
        nextGeTravelAttemptAt = now + GE_TRAVEL_RETRY_MS;

        status = "Opening Grand Exchange";
        if (Rs2GrandExchange.openExchange()) {
            nextGeTravelAttemptAt = 0L;
            return true;
        }

        if (Thread.currentThread().isInterrupted() || shuttingDown) {
            return false;
        }

        status = "Walking to Grand Exchange";
        Rs2GrandExchange.walkToGrandExchange();
        return false;
    }

    private boolean isExpectedInterruption(Throwable throwable) {
        if (shuttingDown || Thread.currentThread().isInterrupted()) {
            return true;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean openBankSafely() {
        if (!closeGrandExchangeInterface()) {
            return false;
        }
        return Rs2Bank.openBank();
    }

    private void clearPendingPurchase() {
        pendingPurchaseType = null;
        pendingItemId = 0;
        pendingItemName = null;
        pendingQuantity = 0;
        pendingPrice = 0;
        pendingOfferStartedAt = 0L;
        pendingCollectReadyAt = 0L;
        pendingAbortCollectReadyAt = 0L;
        pendingBoughtAtAbort = 0;
        pendingAborted = false;
        pendingSlot = null;
    }

    public State getState() {
        return state;
    }

    public String getStatus() {
        return status;
    }

    public AlchOpportunity getActiveOpportunity() {
        return activeOpportunity;
    }

    public List<AlchOpportunity> getRankedOpportunities() {
        return new ArrayList<>(rankedOpportunities);
    }

    public long getCastsCompleted() {
        return castsCompleted;
    }

    public long getProjectedProfit() {
        return projectedProfit;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getProjectedProfitPerHourActual() {
        long runtime = Math.max(1L, System.currentTimeMillis() - startedAt);
        return projectedProfit * TimeUnit.HOURS.toMillis(1) / runtime;
    }

    public boolean isUsingFireStaffMode() {
        return usingFireStaff;
    }

    public boolean isMembersContentEnabled() {
        return membersContentEnabled;
    }

    public boolean isAccountMembershipActive() {
        return accountMembershipActive;
    }

    public boolean isCurrentWorldMembers() {
        return currentWorldMembers;
    }

    public int getCommittedStockRemaining() {
        return committedStockRemaining;
    }

    public int getCoinQuantity() {
        return Rs2Inventory.itemQuantity(COINS_ID);
    }

    public int getNatureRuneQuantity() {
        return Rs2Inventory.itemQuantity(NATURE_RUNE_ID);
    }

    public int getFireRuneQuantity() {
        return Rs2Inventory.itemQuantity(FIRE_RUNE_ID);
    }

    public String getPendingPurchaseSummary() {
        if (!pendingBasket.isEmpty()) {
            long unresolved = pendingBasket.stream().filter(x -> !x.resolved).count();
            return unresolved + "/" + pendingBasket.size() + " basket offers active";
        }
        if (pendingPurchaseType == null || pendingItemName == null || pendingQuantity <= 0) {
            return null;
        }
        String prefix;
        switch (pendingPurchaseType) {
            case NATURE_RUNES:
                prefix = "Nature";
                break;
            case FIRE_RUNES:
                prefix = "Fire";
                break;
            case ALCHABLE:
            default:
                prefix = pendingItemName;
                break;
        }
        return pendingQuantity + " x " + prefix + " @ " + pendingPrice;
    }

    public int getQueuedItemCount() {
        return (int) queuedStockByItem.values().stream().filter(q -> q != null && q > 0).count();
    }

    public int getQueuedStockTotal() {
        long total = queuedStockByItem.values().stream()
                .filter(q -> q != null && q > 0)
                .mapToLong(Integer::longValue)
                .sum();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getPendingBasketOfferCount() {
        return (int) pendingBasket.stream().filter(x -> !x.resolved).count();
    }

    public int getSlowBuyCooldownCount() {
        long now = System.currentTimeMillis();
        return (int) slowBuyCooldownUntil.values().stream().filter(until -> until != null && until > now).count();
    }

    public String getSlowBuyCooldownSummary() {
        long now = System.currentTimeMillis();
        long earliest = slowBuyCooldownUntil.values().stream()
                .filter(until -> until != null && until > now)
                .mapToLong(Long::longValue)
                .min()
                .orElse(0L);
        int count = getSlowBuyCooldownCount();
        if (count <= 0 || earliest <= now) {
            return null;
        }
        long remaining = earliest - now;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60L;
        return count + " active, next " + minutes + "m " + seconds + "s";
    }

    public String getCustomAntibanSummary() {
        if (config == null || !config.customAntiban()) {
            return "Off";
        }
        return config.antibanProfile().name() + " - " + customAntiban.getActivity();
    }

    public int getCustomAntibanBreakCount() {
        return customAntiban.getShortPauses() + customAntiban.getLongBreaks();
    }

    @Override
    public synchronized void shutdown() {
        // Set this before Script.shutdown() cancels/interrupts the worker so any
        // ClientThread.invoke interruption is treated as expected shutdown noise.
        shuttingDown = true;
        nextGeTravelAttemptAt = 0L;

        // Cancel the worker before mutating the state it is reading. With the run()
        // duplicate-loop guard above, this is the one and only active main future.
        super.shutdown();

        clearPendingPurchase();
        rankedOpportunities.clear();
        purchasedInWindow.clear();
        purchaseWindowStartedAtByItem.clear();
        blockedUntil.clear();
        slowBuyCooldownUntil.clear();
        bankStockExhaustedItemIds.clear();
        bankSupplyExhaustedItemIds.clear();
        queuedStockByItem.clear();
        queuedOpportunityByItem.clear();
        inventoryCollectedAtByItem.clear();
        pendingBasket.clear();
        basketPlacementIndex = 0;
        cleanupItemId = 0;
        fireStaffUnavailable = false;
        bankCoinsExhausted = false;
        customAntiban.disabled();
        activeOpportunity = null;
        projectedProfit = 0L;
        castsCompleted = 0L;
        nextCastAllowedAt = 0L;
        committedItemId = 0;
        committedStockRemaining = 0;
        startedAt = 0L;
        lastMarketScanAt = 0L;
        state = State.STARTING;
        status = "Stopped";
    }
}
