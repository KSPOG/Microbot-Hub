package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.script;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.areas.MobArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear.armour.Armour;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.gear.swords.Swords;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.ge.sell.Sell;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.loot.Loot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.needed.Gear;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class CombatScript {


    private static final WorldPoint GOBLIN_AREA_CENTER = new WorldPoint(3252, 3230, 0);
    private static final int AREA_LOOT_RADIUS = 12;

    private String status = "Idle";
    private boolean wasInCombat;
    private long forceLootUntil;

    private static final long FORCE_LOOT_AFTER_KILL_MS = 4_000L;

    public void initialize() {
        status = "Initializing combat";
        wasInCombat = false;
        forceLootUntil = 0L;
    }

    public void shutdown() {
        status = "Combat stopped";
        wasInCombat = false;
        forceLootUntil = 0L;
    }

    public String getStatus() {
        return status;
    }

    public boolean hasCombatSetupReady() {
        return hasFoodInInventory() && hasBestWeaponEquipped() && hasBestArmourEquipped() && isWearingTeamCape();
    }

    public void execute() {
        if (!Microbot.isLoggedIn()) {
            status = "Waiting for login";
            return;
        }

        if (isPlayerBusy()) {
            status = "Waiting for current action";
            return;
        }

        if (!ensureCombatSetup()) {
            return;
        }

        if (!hasCombatSetupReady()) {
            status = "Waiting for required combat gear";
            return;
        }

        updateCombatTransitionState();
        if (forceLootAfterKill()) {
            return;
        }

        if (buryBonesInInventory()) {
            return;
        }

        if (bankWhenInventoryFull()) {
            return;
        }

        if (!ensureBalancedMeleeStatsTraining()) {
            return;
        }

        if (!walkToGoblinArea()) {
            return;
        }

        if (lootTrainingAreaDrops()) {
            return;
        }

        attackGoblin();
    }

    private boolean ensureCombatSetup() {
        if (hasCombatSetupReady()) {
            status = "Combat setup ready";
            return true;
        }

        status = "Opening bank for combat setup";
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        withdrawFood();
        withdrawBestAvailableGear();
        equipInventoryGear();

        if (!hasBestWeaponEquipped() || !hasBestArmourEquipped() || !isWearingTeamCape()) {
            List<String> upgradesToBuy = getMissingUpgradeNames();
            if (upgradesToBuy.isEmpty()) {
                status = "Waiting to equip available gear";
                Rs2Bank.closeBank();
                return false;
            }

            status = "Missing upgrades - going to GE";
            Rs2Bank.closeBank();
            return sellLootAndBuyUpgrades();
        }

        Rs2Bank.closeBank();
        return true;
    }

    private void withdrawFood() {
        if (hasFoodInInventory()) {
            return;
        }

        for (int foodId : Gear.FOOD_REQUIREMENTS) {
            int needed = Math.max(0, Gear.MIN_TROUT_REQUIRED - Rs2Inventory.count(foodId));
            if (needed > 0) {
                Rs2Bank.withdrawX(foodId, needed);
                Global.sleep(250);
            }
        }
    }

    private void withdrawBestAvailableGear() {
        int bestWeapon = bestWeaponForAttackLevel();
        if (!Rs2Equipment.isWearing(bestWeapon) && Rs2Bank.count(bestWeapon) > 0) {
            Rs2Bank.withdrawX(bestWeapon, 1);
            Global.sleep(200);
        }

        for (int armourId : bestArmourForDefenceLevel()) {
            if (!Rs2Equipment.isWearing(armourId) && Rs2Bank.count(armourId) > 0) {
                Rs2Bank.withdrawX(armourId, 1);
                Global.sleep(150);
            }
        }

        if (!Rs2Equipment.isWearing(Gear.AMULET_OF_POWER) && Rs2Bank.count(Gear.AMULET_OF_POWER) > 0) {
            Rs2Bank.withdrawX(Gear.AMULET_OF_POWER, 1);
            Global.sleep(150);
        }

        if (!isWearingTeamCape() && hasTeamCapeInBank()) {
            Rs2Bank.withdrawX(getBestAvailableTeamCapeName(), 1);
            Global.sleep(150);
        }
    }

    private void equipInventoryGear() {
        Rs2Inventory.wield(bestWeaponForAttackLevel());
        for (int armourId : bestArmourForDefenceLevel()) {
            Rs2Inventory.wield(armourId);
        }
        Rs2Inventory.wield(Gear.AMULET_OF_POWER);
        if (!isWearingTeamCape()) {
            String teamCapeInInventory = getTeamCapeInInventoryName();
            if (teamCapeInInventory != null) {
                Rs2Inventory.interact(teamCapeInInventory, "Wear");
            }
        }
    }

    private boolean sellLootAndBuyUpgrades() {
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

        Rs2Bank.setWithdrawAsNote();

        List<Integer> idsToSell = getSellItemIds();
        for (int sellId : idsToSell) {
            int bankCount = Rs2Bank.count(sellId);
            if (bankCount <= 0) {
                continue;
            }

            Rs2Bank.withdrawX(sellId, bankCount);
            Global.sleep(120);
        }
        Rs2Bank.setWithdrawAsItem();
        Rs2Bank.closeBank();

        status = "Selling combat loot";
        int availableSlotsBeforeSelling = Rs2GrandExchange.getAvailableSlotsCount();
        int sellOffersPlaced = 0;
        for (int sellId : idsToSell) {
            String itemName = getItemName(sellId);
            if (itemName == null || itemName.isBlank()) {
                continue;
            }

            int invCount = Rs2Inventory.count(itemName);
            if (invCount <= 0) {
                continue;
            }

            if (placeOffer(itemName, GrandExchangeAction.SELL, invCount)) {
                sellOffersPlaced++;
            }
            Global.sleep(350);
        }

        if (!waitForSellOffersToComplete(availableSlotsBeforeSelling, sellOffersPlaced)) {
            status = "Timed out waiting for sell offers";
            Rs2GrandExchange.closeExchange();
            return false;
        }

        status = "Buying upgrades";
        int availableSlotsBeforeBuying = Rs2GrandExchange.getAvailableSlotsCount();
        int buyOffersPlaced = 0;
        List<String> upgradesToBuy = getMissingUpgradeNames();
        for (String itemName : upgradesToBuy) {
            if (placeOffer(itemName, GrandExchangeAction.BUY, 1)) {
                buyOffersPlaced++;
            }
            Global.sleep(350);
        }

        if (!waitForBuyOffersToComplete(availableSlotsBeforeBuying, buyOffersPlaced)) {
            status = "Timed out waiting for buy offers";
            Rs2GrandExchange.closeExchange();
            return false;
        }

        Rs2GrandExchange.collectAllToBank();
        Rs2GrandExchange.closeExchange();
        return true;
    }

    private List<Integer> getSellItemIds() {
        List<Integer> idsToSell = new ArrayList<>();
        for (int sellId : Sell.DEFAULT_SELL) {
            if (!idsToSell.contains(sellId)) {
                idsToSell.add(sellId);
            }
        }

        int bestWeapon = bestWeaponForAttackLevel();
        for (int weaponId : getAllMeleeWeaponUpgrades()) {
            if (weaponId != bestWeapon && !idsToSell.contains(weaponId)) {
                idsToSell.add(weaponId);
            }
        }

        int[] bestArmourSet = bestArmourForDefenceLevel();
        for (int armourId : getAllMeleeArmourUpgrades()) {
            if (!containsId(bestArmourSet, armourId) && !idsToSell.contains(armourId)) {
                idsToSell.add(armourId);
            }
        }

        return idsToSell;
    }

    private boolean containsId(int[] values, int target) {
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
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

    private boolean waitForSellOffersToComplete(int availableSlotsBeforeSelling, int sellOffersPlaced) {
        if (sellOffersPlaced <= 0) {
            return true;
        }

        long timeoutAt = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < timeoutAt) {
            if (Rs2GrandExchange.hasSoldOffer()) {
                Rs2GrandExchange.collectAllToBank();
                Global.sleep(400);
            }

            if (Rs2GrandExchange.getAvailableSlotsCount() >= availableSlotsBeforeSelling) {
                Rs2GrandExchange.collectAllToBank();
                return true;
            }

            Global.sleep(300);
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
                Global.sleep(400);
            }

            if (Rs2GrandExchange.getAvailableSlotsCount() >= availableSlotsBeforeBuying) {
                Rs2GrandExchange.collectAllToBank();
                return true;
            }

            Global.sleep(300);
        }

        Rs2GrandExchange.collectAllToBank();
        return false;
    }

    private List<String> getMissingUpgradeNames() {
        List<String> names = new ArrayList<>();

        int bestWeapon = bestWeaponForAttackLevel();
        if (!hasItemAvailable(bestWeapon)) {
            String weaponName = getItemName(bestWeapon);
            if (weaponName != null && !names.contains(weaponName)) {
                names.add(weaponName);
            }
        }

        for (int armourId : bestArmourForDefenceLevel()) {
            if (!hasItemAvailable(armourId)) {
                String armourName = getItemName(armourId);
                if (armourName != null && !names.contains(armourName)) {
                    names.add(armourName);
                }
            }
        }

        if (!hasItemAvailable(Gear.AMULET_OF_POWER)) {
            String amuletName = getItemName(Gear.AMULET_OF_POWER);
            if (amuletName != null && !names.contains(amuletName)) {
                names.add(amuletName);
            }
        }

        if (!isWearingTeamCape() && getTeamCapeInInventoryName() == null && !hasTeamCapeInBank()) {
            names.add(Gear.DEFAULT_TEAM_CAPE_NAME);
        }

        return names;
    }

    private boolean hasItemAvailable(int itemId) {
        return Rs2Equipment.isWearing(itemId) || Rs2Inventory.count(itemId) > 0 || Rs2Bank.count(itemId) > 0;
    }

    private String getItemName(int itemId) {
        ItemComposition item = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemComposition(itemId))
                .orElse(null);
        return item == null ? null : item.getName();
    }

    private boolean isWearingTeamCape() {
        for (String capeName : getTeamCapeNameCandidates()) {
            if (Rs2Equipment.isWearing(capeName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTeamCapeInBank() {
        return getBestAvailableTeamCapeName() != null;
    }

    private String getBestAvailableTeamCapeName() {
        return Arrays.stream(getTeamCapeNameCandidates())
                .filter(Rs2Bank::hasItem)
                .findFirst()
                .orElse(null);
    }

    private String getTeamCapeInInventoryName() {
        return Arrays.stream(getTeamCapeNameCandidates())
                .filter(Rs2Inventory::hasItem)
                .findFirst()
                .orElse(null);
    }


    private String[] getTeamCapeNameCandidates() {
        List<String> names = new ArrayList<>();
        names.add(Gear.DEFAULT_TEAM_CAPE_NAME);
        for (int i = 1; i <= 50; i++) {
            names.add("Team-" + i + " cape");
        }
        return names.toArray(new String[0]);
    }

    private boolean buryBonesInInventory() {
        if (Rs2Inventory.interact(ItemID.BONES, "Bury")) {
            status = "Burying bones";
            Global.sleep(350);
            return true;
        }

        if (Rs2Inventory.interact(ItemID.BIG_BONES, "Bury")) {
            status = "Burying big bones";
            Global.sleep(350);
            return true;
        }

        return false;
    }

    private boolean bankWhenInventoryFull() {
        if (!Rs2Inventory.isFull()) {
            return false;
        }

        status = "Inventory full - banking";
        if (!Rs2Bank.walkToBankAndUseBank() || !Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAllExcept(getProtectedInventoryIds());
        Global.sleep(250);

        withdrawFood();
        withdrawBestAvailableGear();
        equipInventoryGear();

        Rs2Bank.closeBank();
        return true;
    }

    private Integer[] getProtectedInventoryIds() {
        List<Integer> protectedIds = new ArrayList<>();
        protectedIds.add(Gear.AMULET_OF_POWER);

        for (int foodId : Gear.FOOD_REQUIREMENTS) {
            protectedIds.add(foodId);
        }
        for (int weaponId : getAllMeleeWeaponUpgrades()) {
            protectedIds.add(weaponId);
        }
        for (int armourId : getAllMeleeArmourUpgrades()) {
            protectedIds.add(armourId);
        }

        return protectedIds.toArray(new Integer[0]);
    }

    private boolean ensureBalancedMeleeStatsTraining() {
        if (Rs2Player.isInCombat() || Rs2Player.isInteracting()) {
            return true;
        }

        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        int strengthLevel = Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
        int defenceLevel = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);

        int targetStyle = 1;
        if (attackLevel <= strengthLevel && attackLevel <= defenceLevel) {
            targetStyle = 0;
        } else if (defenceLevel <= attackLevel && defenceLevel <= strengthLevel) {
            targetStyle = 3;
        }

        int currentStyle = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);
        if (currentStyle == targetStyle) {
            return true;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            Rs2Tab.switchToCombatOptionsTab();
            Global.sleep(250);
        }

        status = "Balancing melee stats";
        Rs2Combat.setAttackStyle(mapStyleWidget(targetStyle));
        Global.sleep(250);
        return true;
    }

    private WidgetInfo mapStyleWidget(int styleIndex) {
        if (styleIndex == 0) {
            return WidgetInfo.COMBAT_STYLE_ONE;
        }
        if (styleIndex == 1) {
            return WidgetInfo.COMBAT_STYLE_TWO;
        }
        if (styleIndex == 2) {
            return WidgetInfo.COMBAT_STYLE_THREE;
        }
        return WidgetInfo.COMBAT_STYLE_FOUR;
    }

    private void updateCombatTransitionState() {
        boolean inCombat = Rs2Player.isInCombat();
        if (wasInCombat && !inCombat) {
            forceLootUntil = System.currentTimeMillis() + FORCE_LOOT_AFTER_KILL_MS;
        }
        wasInCombat = inCombat;
    }

    private boolean forceLootAfterKill() {
        if (System.currentTimeMillis() > forceLootUntil) {
            return false;
        }

        status = "Forcing loot after kill";
        if (lootTrainingAreaDrops()) {
            return true;
        }

        Global.sleep(200);
        return true;
    }

    private boolean walkToGoblinArea() {
        if (MobArea.GOBLINS.contains(Rs2Player.getWorldLocation())) {
            return true;
        }

        if (isPlayerBusy()) {
            return false;
        }

        status = "Walking to goblin area";
        return Rs2Walker.walkTo(GOBLIN_AREA_CENTER);
    }

    private boolean lootTrainingAreaDrops() {
        if (isPlayerBusy()) {
            return false;
        }

        if (Loot.lootCoins(AREA_LOOT_RADIUS)) {
            status = "Looting coins";
            return true;
        }

        for (int lootId : Loot.DEFAULT_LOOT) {
            String lootName = getItemName(lootId);
            if (lootName == null || lootName.isBlank()) {
                continue;
            }

            if (Rs2GroundItem.lootItemsBasedOnNames(new LootingParameters(
                    AREA_LOOT_RADIUS,
                    1,
                    1,
                    0,
                    true,
                    true,
                    lootName
            ))) {
                status = "Looting drops";
                return true;
            }
        }

        return false;
    }

    private void attackGoblin() {
        if (Rs2Player.isInCombat()) {
            status = "Fighting";
            return;
        }

        if (isPlayerBusy()) {
            return;
        }

        status = "Attacking goblin";
        Rs2Npc.attack("Goblin");
    }

    private boolean isPlayerBusy() {
        return Rs2Player.isMoving() || Rs2Player.isInteracting() || Rs2Player.isAnimating();
    }

    private boolean hasFoodInInventory() {
        for (int foodId : Gear.FOOD_REQUIREMENTS) {
            if (Rs2Inventory.count(foodId) >= Gear.MIN_TROUT_REQUIRED) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBestWeaponEquipped() {
        return Rs2Equipment.isWearing(bestWeaponForAttackLevel());
    }

    private boolean hasBestArmourEquipped() {
        for (int armourId : bestArmourForDefenceLevel()) {
            if (!Rs2Equipment.isWearing(armourId)) {
                return false;
            }
        }
        return true;
    }

    private int[] getAllMeleeWeaponUpgrades() {
        return new int[]{
                Swords.RUNE_SCIMITAR,
                Swords.ADAMANT_SCIMITAR,
                Swords.MITHRIL_SCIMITAR,
                Swords.BLACK_SCIMITAR,
                Swords.STEEL_SCIMITAR,
                Swords.IRON_SCIMITAR,
                Swords.BRONZE_SCIMITAR
        };
    }

    private int[] getAllMeleeArmourUpgrades() {
        return new int[]{
                Armour.RUNE_FULL_HELM, Armour.RUNE_PLATEBODY, Armour.RUNE_PLATELEGS, Armour.RUNE_KITESHIELD,
                Armour.ADAMANT_FULL_HELM, Armour.ADAMANT_PLATEBODY, Armour.ADAMANT_PLATELEGS, Armour.ADAMANT_KITESHIELD,
                Armour.MITHRIL_FULL_HELM, Armour.MITHRIL_PLATEBODY, Armour.MITHRIL_PLATELEGS, Armour.MITHRIL_KITESHIELD,
                Armour.BLACK_FULL_HELM, Armour.BLACK_PLATEBODY, Armour.BLACK_PLATELEGS, Armour.BLACK_KITESHIELD,
                Armour.IRON_FULL_HELM, Armour.IRON_PLATEBODY, Armour.IRON_PLATELEGS, Armour.IRON_KITESHIELD,
                Armour.BRONZE_FULL_HELM, Armour.BRONZE_PLATEBODY, Armour.BRONZE_PLATELEGS, Armour.BRONZE_KITESHIELD
        };
    }

    private int bestWeaponForAttackLevel() {
        int attack = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        if (attack >= Swords.RUNE_SCIMITAR_ATTACK_LEVEL) return Swords.RUNE_SCIMITAR;
        if (attack >= Swords.ADAMANT_SCIMITAR_ATTACK_LEVEL) return Swords.ADAMANT_SCIMITAR;
        if (attack >= Swords.MITHRIL_SCIMITAR_ATTACK_LEVEL) return Swords.MITHRIL_SCIMITAR;
        if (attack >= Swords.BLACK_SCIMITAR_ATTACK_LEVEL) return Swords.BLACK_SCIMITAR;
        if (attack >= Swords.STEEL_SCIMITAR_ATTACK_LEVEL) return Swords.STEEL_SCIMITAR;
        if (attack >= Swords.IRON_SCIMITAR_ATTACK_LEVEL) return Swords.IRON_SCIMITAR;
        return Swords.BRONZE_SCIMITAR;
    }

    private int[] bestArmourForDefenceLevel() {
        int defence = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);

        if (defence >= Armour.RUNE_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.RUNE_FULL_HELM, Armour.RUNE_PLATEBODY, Armour.RUNE_PLATELEGS, Armour.RUNE_KITESHIELD);
        }
        if (defence >= Armour.ADAMANT_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.ADAMANT_FULL_HELM, Armour.ADAMANT_PLATEBODY, Armour.ADAMANT_PLATELEGS, Armour.ADAMANT_KITESHIELD);
        }
        if (defence >= Armour.MITHRIL_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.MITHRIL_FULL_HELM, Armour.MITHRIL_PLATEBODY, Armour.MITHRIL_PLATELEGS, Armour.MITHRIL_KITESHIELD);
        }
        if (defence >= Armour.BLACK_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.BLACK_FULL_HELM, Armour.BLACK_PLATEBODY, Armour.BLACK_PLATELEGS, Armour.BLACK_KITESHIELD);
        }
        if (defence >= Armour.IRON_ARMOUR_EQUIP_LEVEL) {
            return armourSet(Armour.IRON_FULL_HELM, Armour.IRON_PLATEBODY, Armour.IRON_PLATELEGS, Armour.IRON_KITESHIELD);
        }

        return armourSet(Armour.BRONZE_FULL_HELM, Armour.BRONZE_PLATEBODY, Armour.BRONZE_PLATELEGS, Armour.BRONZE_KITESHIELD);
    }

    private int[] armourSet(int helm, int body, int legs, int shield) {
        return new int[]{helm, body, legs, shield};
    }
}