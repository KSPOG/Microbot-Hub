package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.script;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.levels.TreeLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.needed.ItemReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.trees.TreeID;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.tools.AxeWCLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.woodcutting.tools.EquipLevels;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Slf4j
public class WoodcuttingScript {
    private static final String CHOP_ACTION = "Chop down";
    private static final int TREE_AREA_RADIUS = 10;
    private static final int BUY_WAIT_TIMEOUT_MS = 45_000;
    private static final int BUY_OFFER_RETRY_COUNT = 3;
    private static final int MIN_BUY_PRICE_GP = 50;
    private static final int MIN_SELL_PRICE_GP = 25;
    private static final double BUY_PRICE_MARKUP_MULTIPLIER = 1.20;
    private static final double SELL_PRICE_DISCOUNT_MULTIPLIER = 0.95;

    private String status = "Idle";

    private static final Axe[] AXE_PRIORITY = new Axe[]{
            new Axe("Rune axe", ItemReqs.RUNE_AXE, AxeWCLevels.RUNE_AXE, EquipLevels.RUNE_AXE, 20_000),
            new Axe("Adamant axe", ItemReqs.ADAMANT_AXE, AxeWCLevels.ADAMANT_AXE, EquipLevels.ADAMANT_AXE, 5_000),
            new Axe("Mithril axe", ItemReqs.MITHRIL_AXE, AxeWCLevels.MITHRIL_AXE, EquipLevels.MITHRIL_AXE, 2_000),
            new Axe("Black axe", ItemReqs.BLACK_AXE, AxeWCLevels.BLACK_AXE, EquipLevels.BLACK_AXE, 1_000),
            new Axe("Steel axe", ItemReqs.STEEL_AXE, AxeWCLevels.STEEL_AXE, EquipLevels.STEEL_AXE, 500),
            new Axe("Bronze axe", ItemReqs.BRONZE_AXE, AxeWCLevels.BRONZE_AXE, EquipLevels.BRONZE_AXE, MIN_BUY_PRICE_GP)
    };

    public void initialize() {
        status = "Initializing woodcutting";
    }

    public void shutdown() {
        status = "Woodcutting stopped";
    }

    public String getStatus() {
        return status;
    }

    public boolean hasRequiredTools() {
        for (int axeId : ItemReqs.ACCEPTED_AXE_IDS) {
            if (Rs2Equipment.isWearing(axeId) || Rs2Inventory.hasItem(axeId)) {
                return true;
            }
        }
        return false;
    }

    public void execute() {
        if (!prepareForTask()) {
            return;
        }

        TreeTarget target = resolveTreeTarget();

        if (Rs2Player.getWorldLocation().distanceTo(target.getLocation()) > TREE_AREA_RADIUS) {
            status = "Walking to " + target.getTreeName();
            Rs2Walker.walkTo(target.getLocation());
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isInteracting() || Rs2Player.isAnimating()) {
            status = "Waiting for current action";
            return;
        }

        var tree = Rs2GameObject.findObject(java.util.Arrays.stream(target.getTreeIds()).boxed().toArray(Integer[]::new));
        if (tree == null) {
            status = "Waiting for " + target.getTreeName();
            return;
        }

        status = "Chopping " + target.getTreeName();
        if (Rs2GameObject.interact(tree, CHOP_ACTION)) {
            Global.sleepUntil(Rs2Player::isAnimating, 3_000);
            Global.sleepUntil(() -> !Rs2Player.isAnimating(), 30_000);
        }
    }


    private boolean prepareForTask() {
        status = "Preparing task";

        if (Rs2Inventory.isFull() && !bankInventoryWhenFull()) {
            return false;
        }

        Axe bestPossibleAxe = getBestUsableAxeForCurrentWoodcuttingLevel();

        if (!bankForBestAvailableAxe(bestPossibleAxe)) {
            log.debug("Unable to secure best woodcutting axe for current levels");
            return false;
        }

        if (bestPossibleAxe != null && !hasAxeEquippedOrInventory(bestPossibleAxe.getItemId())) {
            status = "Preparing required tool";
            return false;
        }

        return true;
    }

    private boolean bankInventoryWhenFull() {
        status = "Banking";

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        if (hasAnyAxeInInventory()) {
            Rs2Bank.depositAllExcept(java.util.Arrays.stream(ItemReqs.ACCEPTED_AXE_IDS).boxed().toArray(Integer[]::new));
        } else {
            Rs2Bank.depositAll();
        }

        Rs2Bank.closeBank();
        return !Rs2Inventory.isFull();
    }

    private boolean hasAnyAxeInInventory() {
        for (int axeId : ItemReqs.ACCEPTED_AXE_IDS) {
            if (Rs2Inventory.hasItem(axeId)) {
                return true;
            }
        }
        return false;
    }

    private boolean bankForBestAvailableAxe(Axe bestPossibleAxe) {
        status = "Banking";

        if (bestPossibleAxe == null) {
            return hasRequiredTools();
        }

        if (hasAxeEquippedOrInventory(bestPossibleAxe.getItemId())) {
            return true;
        }

        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAllExcept(java.util.Arrays.stream(ItemReqs.ACCEPTED_AXE_IDS).boxed().toArray(Integer[]::new));

        if (Rs2Bank.hasItem(bestPossibleAxe.getItemId())) {
            int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
            if (attackLevel >= bestPossibleAxe.getAttackLevel()) {
                Rs2Bank.withdrawAndEquip(bestPossibleAxe.getItemId());
            } else {
                Rs2Bank.withdrawOne(bestPossibleAxe.getItemId());
            }

            Rs2Bank.closeBank();
            return Rs2Equipment.isWearing(bestPossibleAxe.getItemId()) || Rs2Inventory.hasItem(bestPossibleAxe.getItemId());
        }

        withdrawLowerTierAxesForSelling(bestPossibleAxe);
        status = "Buying " + bestPossibleAxe.getName();
        Rs2Bank.closeBank();
        return buyAxeFromGrandExchange(bestPossibleAxe);
    }

    private void withdrawLowerTierAxesForSelling(Axe bestPossibleAxe) {
        for (Axe axe : AXE_PRIORITY) {
            if (axe.getWoodcuttingLevel() >= bestPossibleAxe.getWoodcuttingLevel()) {
                continue;
            }

            if (Rs2Bank.hasItem(axe.getItemId())) {
                Rs2Bank.withdrawAll(axe.getItemId());
            }
        }
    }

    private Axe getBestUsableAxeForCurrentWoodcuttingLevel() {
        int wcLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);

        for (Axe axe : AXE_PRIORITY) {
            if (wcLevel >= axe.getWoodcuttingLevel()) {
                return axe;
            }
        }

        return null;
    }

    private boolean hasAxeEquippedOrInventory(int axeId) {
        return Rs2Equipment.isWearing(axeId) || Rs2Inventory.hasItem(axeId);
    }

    private boolean buyAxeFromGrandExchange(Axe axe) {
        if (!Rs2GrandExchange.walkToGrandExchange()) {
            return false;
        }

        if (!Rs2GrandExchange.openExchange()) {
            return false;
        }

        Global.sleepUntil(Rs2GrandExchange::isOpen, 7_000);
        if (!Rs2GrandExchange.isOpen()) {
            return false;
        }

        if (!ensureExchangeSlotAvailable()) {
            log.debug("No GE slots available for buying {}", axe.getName());
            return false;
        }

        sellLowerTierAxesAtGrandExchange(axe);

        if (!ensureExchangeSlotAvailable()) {
            log.debug("No GE slots available after selling lower tier axes while buying {}", axe.getName());
            return false;
        }

        int buyPrice = getBuyOfferPrice(axe);

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.BUY)
                .itemName(axe.getName())
                .quantity(1)
                .price(buyPrice)
                .closeAfterCompletion(false)
                .build();


        if (!placeBuyOffer(request, axe)) {
            return false;
        }

        boolean boughtAxe = Global.sleepUntil(() -> Rs2GrandExchange.hasBoughtOffer() || Rs2Inventory.hasItem(axe.getItemId()), BUY_WAIT_TIMEOUT_MS);
        if (!boughtAxe) {
            Rs2GrandExchange.abortAllOffers(true);
        }

        Rs2GrandExchange.collectAllToBank();
        if (Rs2Inventory.hasItem(axe.getItemId())) {
            return true;
        }



        if (!placeBuyOffer(request, axe)) {
            return false;
        }

            if (!placeBuyOffer(request, axe)) {
                return false;
            }


        boolean boughtAxe = Global.sleepUntil(() -> Rs2GrandExchange.hasBoughtOffer() || Rs2Inventory.hasItem(axe.getItemId()), BUY_WAIT_TIMEOUT_MS);
        if (!boughtAxe) {
            Rs2GrandExchange.abortAllOffers(true);
        }

        Rs2GrandExchange.collectAllToBank();
        if (Rs2Inventory.hasItem(axe.getItemId())) {
            return true;
        }


        // Fallback verification: bank cache can be stale while GE is open, so check by opening bank.
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        boolean hasAxeInBank = Rs2Bank.hasItem(axe.getItemId());
        Rs2Bank.closeBank();
        return hasAxeInBank;
    }

    private boolean placeBuyOffer(GrandExchangeRequest request, Axe axe) {
        for (int attempt = 1; attempt <= BUY_OFFER_RETRY_COUNT; attempt++) {
            if (Rs2GrandExchange.processOffer(request)) {
                return true;
            }

            log.debug("Failed to create buy offer for {} on attempt {}/{}", axe.getName(), attempt, BUY_OFFER_RETRY_COUNT);
            Global.sleep(600, 900);



            boolean hasAxeInBank = Rs2Bank.hasItem(axe.getItemId());
            Rs2Bank.closeBank();
            return hasAxeInBank;
        }
    }

    private boolean placeBuyOffer(GrandExchangeRequest request, Axe axe) {
        for (int attempt = 1; attempt <= BUY_OFFER_RETRY_COUNT; attempt++) {
            if (Rs2GrandExchange.processOffer(request)) {
                return true;
            }

            log.debug("Failed to create buy offer for {} on attempt {}/{}", axe.getName(), attempt, BUY_OFFER_RETRY_COUNT);
            Global.sleep(600, 900);



            if (!Rs2GrandExchange.isOpen()) {
                if (!Rs2GrandExchange.openExchange()) {
                    continue;
                }
                Global.sleepUntil(Rs2GrandExchange::isOpen, 5_000);
            }
        }

        return false;
    }

    private void sellLowerTierAxesAtGrandExchange(Axe bestPossibleAxe) {
        for (Axe axe : AXE_PRIORITY) {
            if (axe.getWoodcuttingLevel() >= bestPossibleAxe.getWoodcuttingLevel()) {
                continue;
            }

            int quantity = Rs2Inventory.count(axe.getItemId());
            if (quantity <= 0) {
                continue;
            }

            if (!ensureExchangeSlotAvailable()) {
                return;
            }

            int sellPrice = getSellOfferPrice(axe);

            GrandExchangeRequest sellRequest = GrandExchangeRequest.builder()
                    .action(GrandExchangeAction.SELL)
                    .itemName(axe.getName())
                    .quantity(quantity)
                    .price(sellPrice)
                    .closeAfterCompletion(false)
                    .build();

            if (Rs2GrandExchange.processOffer(sellRequest)) {
                Global.sleepUntil(() -> !Rs2Inventory.hasItem(axe.getItemId()), 7_000);
                Rs2GrandExchange.collectAllToBank();
            }
        }
    }


    private int getBuyOfferPrice(Axe axe) {
        int marketPrice = Microbot.getItemManager().getItemPrice(axe.getItemId());
        if (marketPrice <= 0) {
            log.debug("Missing market price for {} ({}), falling back to tier price {}", axe.getName(), axe.getItemId(), axe.getFallbackBuyPrice());
            return axe.getFallbackBuyPrice();
        }

        return Math.max(MIN_BUY_PRICE_GP, (int) Math.ceil(marketPrice * BUY_PRICE_MARKUP_MULTIPLIER));
    }

    private int getSellOfferPrice(Axe axe) {
        int marketPrice = Microbot.getItemManager().getItemPrice(axe.getItemId());
        if (marketPrice <= 0) {
            log.debug("Missing market price for {} ({}), falling back to min sell price", axe.getName(), axe.getItemId());
            return MIN_SELL_PRICE_GP;
        }

        return Math.max(MIN_SELL_PRICE_GP, (int) Math.floor(marketPrice * SELL_PRICE_DISCOUNT_MULTIPLIER));
    }

    private boolean ensureExchangeSlotAvailable() {
        if (Rs2GrandExchange.getAvailableSlotsCount() > 0) {
            return true;
        }

        Rs2GrandExchange.collectAllToBank();
        Global.sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0, 5_000);
        if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
            Rs2GrandExchange.abortAllOffers(true);
            Global.sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0, 5_000);
        }

        return Rs2GrandExchange.getAvailableSlotsCount() > 0;
    }

    private TreeTarget resolveTreeTarget() {
        int wcLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        int combatLevel = Microbot.getClientThread().invoke(() ->
                Microbot.getClient().getLocalPlayer() != null
                        ? Microbot.getClient().getLocalPlayer().getCombatLevel()
                        : 0
        );

        if (wcLevel >= TreeLevels.YEW) {
            return new TreeTarget("Yew", Areas.YEW, TreeID.YEW);
        }

        if (wcLevel >= TreeLevels.WILLOW) {
            return new TreeTarget("Willow", Areas.WILLOW, TreeID.WILLOW);
        }

        if (wcLevel >= TreeLevels.OAK) {
            return new TreeTarget("Oak", combatLevel >= 40 ? Areas.OAK_DRAYNOR : Areas.OAK_VARROCK, TreeID.OAK);
        }

        return new TreeTarget("Tree", Areas.TREE, TreeID.NORMAL);
    }

    private static final class Axe {
        private final String name;
        private final int itemId;
        private final int woodcuttingLevel;
        private final int attackLevel;
        private final int fallbackBuyPrice;

        private Axe(String name, int itemId, int woodcuttingLevel, int attackLevel, int fallbackBuyPrice) {
            this.name = name;
            this.itemId = itemId;
            this.woodcuttingLevel = woodcuttingLevel;
            this.attackLevel = attackLevel;
            this.fallbackBuyPrice = fallbackBuyPrice;
        }

        private String getName() {
            return name;
        }

        private int getItemId() {
            return itemId;
        }

        private int getWoodcuttingLevel() {
            return woodcuttingLevel;
        }

        private int getAttackLevel() {
            return attackLevel;
        }

        private int getFallbackBuyPrice() {
            return fallbackBuyPrice;
        }
    }

    private static final class TreeTarget {
        private final String treeName;
        private final WorldPoint location;
        private final int[] treeIds;

        private TreeTarget(String treeName, WorldPoint location, int[] treeIds) {
            this.treeName = treeName;
            this.location = location;
            this.treeIds = treeIds;
        }

        private String getTreeName() {
            return treeName;
        }

        private WorldPoint getLocation() {
            return location;
        }

        private int[] getTreeIds() {
            return treeIds;
        }
    }
}
