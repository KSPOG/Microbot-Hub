package net.runelite.client.plugins.microbot.kspbattlestaffbuyer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class KspBattlestaffBuyerScript extends Script
{
    private static final int BATTLESTAFF_ID = 1391;
    private static final String BATTLESTAFF = "Battlestaff";
    private static final String COINS = "Coins";
    private static final String ZAFF = "Zaff";
    private static final int ZAFF_BASE_STOCK = 5;
    private static final int ZAFF_BASE_PRICE = 7_000;
    private static final int ZAFF_PRICE_STEP = 140; // 2% of 7,000 per staff below base stock
    private static final double GE_TAX_RATE = 0.02D;

    private static final WorldPoint ZAFF_TILE = new WorldPoint(3203, 3434, 0);
    private static final WorldPoint VARROCK_WEST_BANK = new WorldPoint(3185, 3436, 0);

    private static final long LOOP_MS = 100L;
    private static final long SHOP_ACTION_DELAY_MS = 450L;
    private static final long INTERACT_RETRY_MS = 900L;
    private static final long HOP_RETRY_MS = 1_200L;
    private static final long WIDGET_RETRY_MS = 350L;

    private final KspBattlestaffBuyerPlugin plugin;
    private KspBattlestaffBuyerConfig config;

    private volatile String status = "Starting";
    private volatile long startedAtMs;
    private volatile long battlestavesBought;
    private volatile long bankTrips;
    private volatile long worldHops;
    private volatile long geSellOffers;
    private volatile int inventoryBattlestaves;
    private volatile int bankBattlestaves;
    private volatile int coinsRemaining;
    private volatile int shopStock;

    private int lastBattlestaffCount = -1;
    private long nextActionAt;
    private long nextHopAttemptAt;
    private boolean bankTripHadStaves;
    private boolean pendingHop;
    private boolean sellingCycle;
    private boolean geOfferPlaced;
    private boolean geSaleCollected;
    private long cycleAcquisitionCost;
    private int cycleAcquiredStaffs;
    private int lastCoinCount = -1;
    private int activeSellQuantity;
    private int activeSellPrice;

    @Inject
    public KspBattlestaffBuyerScript(KspBattlestaffBuyerPlugin plugin)
    {
        this.plugin = plugin;
    }

    public boolean run(KspBattlestaffBuyerConfig config)
    {
        this.config = config;
        reset();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn() || !super.run())
                {
                    return;
                }

                refreshSnapshot();
                trackPurchases();
                process();
            }
            catch (Exception ex)
            {
                for (Throwable cause = ex; cause != null; cause = cause.getCause())
                {
                    if (cause instanceof InterruptedException)
                    {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                status = "Error - check client log";
                log.error("KSP Battlestaff Buyer loop error", ex);
            }
        }, 0L, LOOP_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void process()
    {
        if (Microbot.isHopping())
        {
            status = "Hopping worlds";
            return;
        }

        // Never world-hop until every blocking interface is confirmed closed.
        if (pendingHop)
        {
            processPendingHop();
            return;
        }

        if (sellingCycle)
        {
            processSellingCycle();
            return;
        }

        if (Rs2Bank.isOpen())
        {
            handleBank();
            return;
        }

        if (coinsRemaining < ZAFF_BASE_PRICE && cycleAcquiredStaffs > 0)
        {
            if (Rs2Shop.isOpen())
            {
                status = "Out of buying cash - closing shop";
                Rs2Shop.closeShop();
                nextActionAt = System.currentTimeMillis() + WIDGET_RETRY_MS;
                return;
            }

            sellingCycle = true;
            geOfferPlaced = false;
            geSaleCollected = false;
            bank();
            return;
        }

        if (Rs2Inventory.isFull() || coinsRemaining <= 0)
        {
            if (Rs2Shop.isOpen())
            {
                status = "Closing shop before banking";
                Rs2Shop.closeShop();
                nextActionAt = System.currentTimeMillis() + WIDGET_RETRY_MS;
                return;
            }

            bank();
            return;
        }

        if (Rs2Shop.isOpen())
        {
            handleShop();
            return;
        }

        openZaffShop();
    }

    private void openZaffShop()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            status = "Waiting for player";
            return;
        }

        if (player.getPlane() != ZAFF_TILE.getPlane() || player.distanceTo(ZAFF_TILE) > 5)
        {
            status = "Walking to Zaff";
            Rs2Walker.walkTo(ZAFF_TILE, 4);
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionAt)
        {
            return;
        }

        // The widget must be closed before interacting with Zaff.
        if (Rs2Shop.isOpen())
        {
            status = "Waiting for shop widget";
            return;
        }

        Rs2NpcModel zaff = Rs2Npc.getNpc(ZAFF, true);
        if (zaff == null)
        {
            status = "Zaff not found";
            nextActionAt = now + INTERACT_RETRY_MS;
            return;
        }

        status = "Opening Zaff's shop";
        Rs2Npc.interact(zaff, "Trade");
        nextActionAt = now + INTERACT_RETRY_MS;
    }

    private void handleShop()
    {
        long now = System.currentTimeMillis();

        // Buy logic only runs while the actual shop widget is visible.
        if (!Rs2Shop.isOpen())
        {
            status = "Waiting for shop widget";
            return;
        }

        if (Rs2Inventory.isFull())
        {
            status = "Inventory full - closing shop";
            Rs2Shop.closeShop();
            nextActionAt = now + WIDGET_RETRY_MS;
            return;
        }

        shopStock = currentShopStock();

        int nextCost = nextBattlestaffCost(shopStock);
        if (shopStock > 0 && cycleAcquiredStaffs > 0 && coinsRemaining < nextCost)
        {
            sellingCycle = true;
            geOfferPlaced = false;
            geSaleCollected = false;
            status = "Cannot afford next staff - closing shop";
            Rs2Shop.closeShop();
            nextActionAt = now + WIDGET_RETRY_MS;
            return;
        }

        if (shopStock <= 0)
        {
            // Do NOT hop in this same loop. First close, then verify closure
            // from processPendingHop() on subsequent loops.
            pendingHop = true;
            status = "Out of stock - closing shop";
            Rs2Shop.closeShop();
            nextActionAt = now + WIDGET_RETRY_MS;
            return;
        }

        if (now < nextActionAt)
        {
            return;
        }

        // Final widget gate immediately before the shop invocation.
        if (!Rs2Shop.isOpen())
        {
            status = "Shop closed - reopening";
            return;
        }

        status = "Buying Battlestaves (" + shopStock + " in stock)";
        Rs2Shop.buyItem(BATTLESTAFF_ID, "50");
        nextActionAt = now + SHOP_ACTION_DELAY_MS;
    }

    private void processPendingHop()
    {
        long now = System.currentTimeMillis();

        if (Rs2Shop.isOpen())
        {
            status = "Waiting for Zaff shop to close";
            if (now >= nextActionAt)
            {
                Rs2Shop.closeShop();
                nextActionAt = now + WIDGET_RETRY_MS;
            }
            return;
        }

        if (Rs2Bank.isOpen())
        {
            status = "Closing bank before hop";
            Rs2Bank.closeBank();
            return;
        }

        if (Rs2GrandExchange.isOfferScreenOpen())
        {
            status = "Closing GE offer before hop";
            Rs2GrandExchange.backToOverview();
            return;
        }

        if (Rs2GrandExchange.isOpen())
        {
            status = "Closing GE before hop";
            Rs2GrandExchange.closeExchange();
            return;
        }

        if (now < nextHopAttemptAt)
        {
            return;
        }

        hopWorld();
    }

    private void hopWorld()
    {
        // Hard guard: a world-hop may never be invoked with a shop widget open.
        if (Rs2Shop.isOpen()
                || Rs2Bank.isOpen()
                || Rs2GrandExchange.isOpen()
                || Rs2GrandExchange.isOfferScreenOpen())
        {
            status = "Waiting for widgets before hop";
            return;
        }

        long now = System.currentTimeMillis();
        int current = Rs2Player.getWorld();
        int target = -1;

        for (int i = 0; i < 12; i++)
        {
            int candidate = Rs2WorldUtil.getRandomAccessibleWorld(true, true, true);

            // Hard P2P-only gate. Do not trust a fallback candidate unless the
            // world list itself confirms that it is a MEMBERS world.
            if (candidate != -1 && candidate != current && isMembersWorld(candidate))
            {
                target = candidate;
                break;
            }
        }

        if (target == -1 || target == current)
        {
            status = "No suitable P2P world found";
            nextHopAttemptAt = now + HOP_RETRY_MS;
            return;
        }

        // Final validation immediately before the hop.
        if (!isMembersWorld(target))
        {
            status = "Rejected non-P2P world";
            nextHopAttemptAt = now + HOP_RETRY_MS;
            return;
        }

        status = "Hopping to P2P world " + target;

        int beforeWorld = Rs2Player.getWorld();
        boolean invoked = Microbot.hopToWorld(target);

        // Do not rely only on the immediate boolean return. Depending on client
        // timing, the hop may already have been dispatched while the method
        // still returns false.
        if (invoked || Microbot.isHopping() || Rs2Player.getWorld() != beforeWorld)
        {
            worldHops++;
            pendingHop = false;
            nextActionAt = 0L;
        }
        else
        {
            status = "World hop not started - retrying";
        }

        nextHopAttemptAt = now + HOP_RETRY_MS;
    }

    private void bank()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            status = "Waiting for player";
            return;
        }

        if (player.getPlane() != VARROCK_WEST_BANK.getPlane()
                || player.distanceTo(VARROCK_WEST_BANK) > 5)
        {
            status = "Walking to Varrock bank";
            Rs2Walker.walkTo(VARROCK_WEST_BANK, 3);
            return;
        }

        if (Rs2Inventory.isItemSelected())
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            return;
        }

        status = "Selecting random bank target";
        openRandomBankTarget();
    }

    private void openRandomBankTarget()
    {
        if (Rs2Bank.isOpen())
        {
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            return;
        }

        List<TileObject> bankObjects = Rs2GameObject.getAll(object ->
        {
            if (object == null || object.getWorldLocation() == null
                    || object.getWorldLocation().distanceTo(player) > 10)
            {
                return false;
            }

            ObjectComposition composition = Rs2GameObject.getObjectComposition(object.getId());
            if (composition == null || composition.getName() == null)
            {
                return false;
            }

            String name = composition.getName().toLowerCase();
            if (!name.contains("bank booth") && !name.contains("bank chest"))
            {
                return false;
            }

            String[] actions = composition.getActions();
            if (actions == null)
            {
                return false;
            }

            for (String action : actions)
            {
                if (action != null && action.equalsIgnoreCase("Bank"))
                {
                    return true;
                }
            }
            return false;
        }, 10);

        List<Rs2NpcModel> bankers = Rs2Npc.getNpcs(npc ->
        {
            if (npc == null || npc.getName() == null || npc.getWorldLocation() == null)
            {
                return false;
            }

            return npc.getName().equalsIgnoreCase("Banker")
                    && npc.getWorldLocation().distanceTo(player) <= 10
                    && Rs2Npc.hasAction(npc.getId(), "Bank");
        }).collect(Collectors.toList());

        int objectCount = bankObjects.size();
        int bankerCount = bankers.size();
        int total = objectCount + bankerCount;

        if (total <= 0)
        {
            status = "Opening nearest bank";
            Rs2Bank.openBank();
            return;
        }

        int pick = ThreadLocalRandom.current().nextInt(total);

        if (pick < objectCount)
        {
            TileObject target = bankObjects.get(pick);
            status = "Banking at random booth";
            if (!Rs2Bank.openBank(target))
            {
                status = "Bank target failed - retrying";
            }
            return;
        }

        Rs2NpcModel banker = bankers.get(pick - objectCount);
        status = "Banking with random banker";
        if (!Rs2Bank.openBank(banker))
        {
            status = "Banker failed - retrying";
        }
    }

    private void handleBank()
    {
        if (!Rs2Bank.isOpen())
        {
            return;
        }

        if (inventoryBattlestaves > 0)
        {
            bankTripHadStaves = true;
        }

        boolean hasNonCoins = Rs2Inventory.all().stream()
                .anyMatch(item -> item != null && !COINS.equalsIgnoreCase(item.getName()));

        if (hasNonCoins)
        {
            status = "Banking Battlestaves";
            Rs2Bank.depositAllExcept(true, COINS);
            return;
        }

        bankBattlestaves = Math.max(0, Rs2Bank.count(BATTLESTAFF_ID));

        if (bankTripHadStaves)
        {
            bankTrips++;
            bankTripHadStaves = false;
        }

        // Selling is cash-driven now. There is no bank-stock threshold.
        // Once available cash can no longer buy another Battlestaff, liquidate
        // exactly the Battlestaves acquired by this plugin cycle.
        if (cycleAcquiredStaffs > 0 && coinCount() < ZAFF_BASE_PRICE)
        {
            sellingCycle = true;
            geOfferPlaced = false;
            geSaleCollected = false;
            prepareBattlestavesForSale();
            return;
        }

        if (coinCount() <= 0)
        {
            int bankCoins = Rs2Bank.count(COINS, true);
            if (bankCoins <= 0)
            {
                status = "Out of coins - stopped";
                Microbot.showMessage("KSP Battlestaff Buyer: no coins left in inventory or bank.");
                Microbot.stopPlugin(plugin);
                return;
            }

            status = "Withdrawing coins";
            Rs2Bank.setWithdrawAsItem();
            Rs2Bank.withdrawAll(COINS, true);
            return;
        }

        status = "Returning to Zaff";
        Rs2Bank.closeBank();
        nextActionAt = 0L;
    }

    private void prepareBattlestavesForSale()
    {
        if (!Rs2Bank.isOpen())
        {
            status = "Opening bank for GE sale";
            bank();
            return;
        }

        int bankCount = Math.max(0, Rs2Bank.count(BATTLESTAFF_ID));
        int quantityToSell = bankCount;

        if (quantityToSell <= 0)
        {
            status = "No Battlestaves to sell";
            sellingCycle = false;
            return;
        }

        if (inventoryBattlestaffQuantityAnyForm() <= 0)
        {
            status = "Withdrawing all " + quantityToSell + " Battlestaves as notes";
            if (!Rs2Bank.setWithdrawAsNote())
            {
                return;
            }

            Rs2Bank.withdrawAll(BATTLESTAFF_ID);
            return;
        }

        status = "Going to Grand Exchange";
        Rs2Bank.closeBank();
    }

    private void processSellingCycle()
    {
        // Selling also respects interface state. Shop must be gone before
        // banking/GE actions can continue.
        if (Rs2Shop.isOpen())
        {
            status = "Closing shop for GE sale";
            Rs2Shop.closeShop();
            return;
        }

        if (Rs2Bank.isOpen())
        {
            prepareBattlestavesForSale();
            return;
        }

        int quantity = inventoryBattlestaffQuantityAnyForm();

        if (geOfferPlaced)
        {
            if (!Rs2GrandExchange.isOpen())
            {
                status = "Opening GE to check sale";
                Rs2GrandExchange.openExchange();
                return;
            }

            if (Rs2GrandExchange.isOfferScreenOpen())
            {
                status = "Returning to GE overview";
                Rs2GrandExchange.backToOverview();
                return;
            }

            if (!Rs2GrandExchange.hasFinishedSellingOffers())
            {
                status = "Waiting for profitable GE sale";
                return;
            }

            if (!geSaleCollected)
            {
                status = "Collecting GE sale proceeds";
                if (Rs2GrandExchange.collectAllToInventory())
                {
                    geSaleCollected = true;
                }
                return;
            }

            if (Rs2GrandExchange.isOpen())
            {
                status = "Closing Grand Exchange";
                Rs2GrandExchange.closeExchange();
                return;
            }

            sellingCycle = false;
            geOfferPlaced = false;
            geSaleCollected = false;
            bankBattlestaves = 0;
            cycleAcquisitionCost = 0L;
            cycleAcquiredStaffs = 0;
            activeSellQuantity = 0;
            activeSellPrice = 0;
            lastCoinCount = coinCount();
            status = "Sale collected - returning to Zaff";
            nextActionAt = 0L;
            return;
        }

        if (quantity <= 0)
        {
            // We entered a sale cycle but do not have the noted stack yet.
            // Return to the nearest bank and prepare it again.
            status = "Preparing Battlestaves for GE";
            if (Rs2GrandExchange.isOpen())
            {
                Rs2GrandExchange.closeExchange();
                return;
            }
            bank();
            return;
        }

        if (!Rs2GrandExchange.isOpen())
        {
            status = "Walking to Grand Exchange";
            if (!Rs2GrandExchange.walkToGrandExchange())
            {
                return;
            }

            status = "Opening Grand Exchange";
            Rs2GrandExchange.openExchange();
            return;
        }

        if (Rs2GrandExchange.isOfferScreenOpen())
        {
            status = "Waiting for GE overview";
            Rs2GrandExchange.backToOverview();
            return;
        }

        int quantityToSell = quantity;
        int minimumProfitPrice = minimumProfitableSellPrice(quantityToSell);

        final int price;
        if (config.useCustomSellPrice())
        {
            price = Math.max(1, config.customSellPrice());
            status = "Selling " + quantityToSell + " @ " + price + " (custom price)";
        }
        else
        {
            WikiPrice market = Rs2GrandExchange.getRealTimePrices(BATTLESTAFF_ID);
            int marketSellPrice = market == null ? 0 : market.sellPrice;

            // Use the live instant-sell side when it is already profitable.
            // If the live market is below our break-even+profit floor, list at the
            // minimum profitable price and wait rather than deliberately taking a loss.
            price = Math.max(minimumProfitPrice, marketSellPrice);
            status = "Selling " + quantityToSell + " @ " + price + " (profit protected)";
        }

        activeSellQuantity = quantityToSell;
        activeSellPrice = price;

        if (Rs2GrandExchange.sellItem(BATTLESTAFF, quantityToSell, price))
        {
            geSellOffers++;
            geOfferPlaced = true;
            geSaleCollected = false;
            status = "Battlestaff sell offer placed";
        }
        else
        {
            status = "GE sell failed - retrying";
        }
    }

    private void trackPurchases()
    {
        int currentStaffs = battlestaffCountUnnoted();
        int currentCoins = coinCount();

        if (lastBattlestaffCount >= 0 && currentStaffs > lastBattlestaffCount)
        {
            int bought = currentStaffs - lastBattlestaffCount;
            battlestavesBought += bought;
            cycleAcquiredStaffs += bought;

            if (lastCoinCount >= 0 && currentCoins < lastCoinCount)
            {
                cycleAcquisitionCost += (long) lastCoinCount - currentCoins;
            }

            nextActionAt = 0L;
        }

        lastBattlestaffCount = currentStaffs;
        lastCoinCount = currentCoins;
    }

    private void refreshSnapshot()
    {
        inventoryBattlestaves = battlestaffCountUnnoted();
        coinsRemaining = coinCount();
        shopStock = Rs2Shop.isOpen() ? currentShopStock() : 0;

        if (Rs2Bank.isOpen())
        {
            bankBattlestaves = Math.max(0, Rs2Bank.count(BATTLESTAFF_ID));
        }
    }

    private int currentShopStock()
    {
        if (!Rs2Shop.isOpen())
        {
            return 0;
        }

        return Rs2Shop.shopItems.stream()
                .filter(item -> item != null && item.getId() == BATTLESTAFF_ID)
                .mapToInt(item -> Math.max(0, item.getQuantity()))
                .sum();
    }

    private int battlestaffCountUnnoted()
    {
        return Math.max(0, Rs2Inventory.itemQuantity(BATTLESTAFF_ID));
    }

    private int inventoryBattlestaffQuantityAnyForm()
    {
        return Rs2Inventory.all().stream()
                .filter(item -> item != null && BATTLESTAFF.equalsIgnoreCase(item.getName()))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private int coinCount()
    {
        return Math.max(0, Rs2Inventory.itemQuantity(COINS, true));
    }

    private int nextBattlestaffCost(int stock)
    {
        if (stock <= 0)
        {
            return Integer.MAX_VALUE;
        }

        int unitsBelowBase = Math.max(0, ZAFF_BASE_STOCK - stock);
        return ZAFF_BASE_PRICE + unitsBelowBase * ZAFF_PRICE_STEP;
    }

    private int minimumProfitableSellPrice(int quantity)
    {
        if (quantity <= 0)
        {
            return ZAFF_BASE_PRICE + 1;
        }

        long trackedCost = Math.max(0L, cycleAcquisitionCost);

        // We may now be selling Battlestaves that were already in the bank before
        // this cycle. Their exact acquisition cost is unknown, so protect them with
        // at least Zaff's 7,000 gp base-cost assumption rather than pretending they
        // were free.
        long conservativeCost = Math.max(trackedCost, (long) quantity * ZAFF_BASE_PRICE);

        int price = (int) Math.max(1L,
                (long) Math.ceil((conservativeCost + 1.0D) / quantity / (1.0D - GE_TAX_RATE)));

        // Account for OSRS GE tax rounding exactly enough to guarantee net proceeds
        // exceed the tracked/conservative acquisition cost.
        while ((long) quantity * netAfterTaxPerItem(price) <= conservativeCost)
        {
            price++;
        }

        return price;
    }

    private int netAfterTaxPerItem(int grossPrice)
    {
        int tax = (int) Math.floor(grossPrice * GE_TAX_RATE);
        return grossPrice - tax;
    }

    private boolean isMembersWorld(int worldId)
    {
        try
        {
            WorldResult worlds = Microbot.getWorldService().getWorlds();
            if (worlds == null || worlds.getWorlds() == null)
            {
                return false;
            }

            for (World world : worlds.getWorlds())
            {
                if (world != null && world.getId() == worldId)
                {
                    return world.getTypes() != null
                            && world.getTypes().contains(WorldType.MEMBERS);
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("Unable to verify P2P world {}: {}", worldId, ex.getMessage());
        }

        return false;
    }

    private void reset()
    {
        startedAtMs = System.currentTimeMillis();
        battlestavesBought = 0L;
        bankTrips = 0L;
        worldHops = 0L;
        geSellOffers = 0L;
        inventoryBattlestaves = 0;
        bankBattlestaves = 0;
        coinsRemaining = 0;
        shopStock = 0;
        lastBattlestaffCount = -1;
        nextActionAt = 0L;
        nextHopAttemptAt = 0L;
        bankTripHadStaves = false;
        pendingHop = false;
        sellingCycle = false;
        geOfferPlaced = false;
        geSaleCollected = false;
        cycleAcquisitionCost = 0L;
        cycleAcquiredStaffs = 0;
        lastCoinCount = -1;
        activeSellQuantity = 0;
        activeSellPrice = 0;
        status = "Starting";
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        status = "Stopped";
    }

    public String getStatus() { return status; }
    public long getRuntimeMs() { return Math.max(0L, System.currentTimeMillis() - startedAtMs); }
    public long getBattlestavesBought() { return battlestavesBought; }
    public long getBankTrips() { return bankTrips; }
    public long getWorldHops() { return worldHops; }
    public long getGeSellOffers() { return geSellOffers; }
    public int getInventoryBattlestaves() { return inventoryBattlestaves; }
    public int getBankBattlestaves() { return bankBattlestaves; }
    public int getCoinsRemaining() { return coinsRemaining; }
    public int getShopStock() { return shopStock; }
    public int getCycleAcquiredStaffs() { return cycleAcquiredStaffs; }
    public long getCycleAcquisitionCost() { return cycleAcquisitionCost; }
    public int getActiveSellQuantity() { return activeSellQuantity; }
    public int getActiveSellPrice() { return activeSellPrice; }
    public int getMinimumProfitableSellPrice()
    {
        int qty = activeSellQuantity > 0 ? activeSellQuantity : Math.max(1, cycleAcquiredStaffs);
        return minimumProfitableSellPrice(qty);
    }

    public boolean isUsingCustomSellPrice()
    {
        return config != null && config.useCustomSellPrice();
    }

    public int getConfiguredCustomSellPrice()
    {
        return config == null ? 0 : config.customSellPrice();
    }

    public long getBattlestavesPerHour()
    {
        long runtime = getRuntimeMs();
        return runtime <= 0L ? 0L : Math.round(battlestavesBought * 3_600_000.0D / runtime);
    }
}
